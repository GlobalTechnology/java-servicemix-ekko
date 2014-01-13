package org.ccci.gto.servicemix.ekko.cloudvideo;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import javax.persistence.PersistenceContext;
import javax.persistence.PersistenceException;
import javax.persistence.TypedQuery;

import org.ccci.gto.persistence.tx.TransactionService;
import org.ccci.gto.servicemix.ekko.cloudvideo.model.AwsFile;
import org.ccci.gto.servicemix.ekko.cloudvideo.model.AwsFileToUpload;
import org.ccci.gto.servicemix.ekko.cloudvideo.model.AwsJob;
import org.ccci.gto.servicemix.ekko.cloudvideo.model.AwsOutput;
import org.ccci.gto.servicemix.ekko.cloudvideo.model.AwsOutput.Type;
import org.ccci.gto.servicemix.ekko.cloudvideo.model.Video;
import org.ccci.gto.servicemix.ekko.cloudvideo.model.Video.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public class VideoStateMachine {
    private static final Logger LOG = LoggerFactory.getLogger(VideoStateMachine.class);

    private static final int CHECK_ENCODES_SLICE_SIZE = 100;
    private static final int UPLOADS_SLICE_SIZE = 100;

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private TransactionService txService;

    @Autowired
    private VideoManager manager;

    @Autowired
    private AwsVideoController aws;

    public boolean enqueueUpload(final Video video, final AwsFile file, final boolean deleteAfterUpload) {
        // short-circuit if we don't have a video object
        if (video == null) {
            return false;
        }

        // enqueue the upload
        try {
            txService.inTransaction(new Runnable() {
                @Override
                public void run() {
                    final AwsFileToUpload upload = new AwsFileToUpload(manager.refresh(video), file);
                    upload.setDeleteSource(deleteAfterUpload);
                    em.persist(upload);
                }
            });
        } catch (final Exception e) {
            LOG.error("error enqueuing upload", e);
            return false;
        }

        return true;
    }

    public void processPendingUploads() {
        // fetch a list of videos with pending uploads
        final List<Video> pending;
        try {
            pending = this.txService.inReadOnlyTransaction(new Callable<List<Video>>() {
                @Override
                public List<Video> call() throws Exception {
                    final TypedQuery<Video> query = em.createNamedQuery("AwsFileToUpload.pendingVideos", Video.class);
                    query.setMaxResults(UPLOADS_SLICE_SIZE);
                    return query.getResultList();
                }
            });
        } catch (final Exception e) {
            LOG.error("error retrieving pending uploads for processUploads", e);
            return;
        }

        // process any found pending uploads
        for (final Video video : pending) {
            // lock the video for processing
            if (!this.acquireLock(video)) {
                continue;
            }

            try {
                // retrieve the most recent upload request, deleting all stale ones in the process
                final AwsFileToUpload upload = this.txService.inTransaction(new Callable<AwsFileToUpload>() {
                    @Override
                    public AwsFileToUpload call() throws Exception {
                        final TypedQuery<AwsFileToUpload> query = em.createNamedQuery("AwsFileToUpload.pendingUploads",
                                AwsFileToUpload.class);
                        query.setParameter("video", manager.refresh(video));
                        final List<AwsFileToUpload> files = query.getResultList();

                        // get the latest upload for this video
                        final AwsFileToUpload upload = files.size() > 0 ? files.get(files.size() - 1) : null;

                        // don't process any requests other than the most recent one
                        if (files.size() > 1) {
                            final AwsFile file = upload.getFile();
                            for (final AwsFileToUpload staleUpload : files.subList(0, files.size() - 1)) {
                                // we only honor deleteSource if the file is not the same as the most recent upload
                                // request
                                final boolean deleteFile = staleUpload.isDeleteSource()
                                        && !(file != null && file.equals(staleUpload.getFile()));
                                delete(staleUpload, deleteFile);
                            }
                        }

                        // return the upload being processed
                        return upload;
                    }
                });

                // is there an upload to process?
                if (upload != null) {
                    final boolean success = aws.updateMaster(video, upload.getFile());

                    // we successfully moved the upload, so cleanup the item in
                    // the queue
                    if (success) {
                        txService.inTransaction(new Runnable() {
                            @Override
                            public void run() {
                                delete(em.merge(upload), upload.isDeleteSource());
                            }
                        });

                        // cancel any stale encoding jobs
                        aws.cleanupStaleEncodingJobs(video);

                        // schedule starting the pending encodes
                        aws.scheduleProcessStartEncodes();
                    }
                }
            } catch (final Exception e) {
                // log the error, but don't break processing of the next video
                LOG.debug("processUploads() error", e);
            } finally {
                // release the video lock
                try {
                    this.releaseLock(video);
                } catch (final Exception e) {
                    // log exception, but don't propagate it
                    LOG.error("error trying to clear video lock", e);
                }
            }
        }
    }

    public void checkForMissingOutputs() {
        // fetch a list of videos in the check state
        final List<Video> videos;
        try {
            videos = this.txService.inReadOnlyTransaction(new Callable<List<Video>>() {
                @Override
                public List<Video> call() throws Exception {
                    final TypedQuery<Video> query = em.createNamedQuery("Video.findByState", Video.class);
                    query.setParameter("state", State.CHECK);
                    query.setMaxResults(CHECK_ENCODES_SLICE_SIZE);
                    return query.getResultList();
                }
            });
        } catch (final Exception e) {
            LOG.error("error retrieving videos in CHECK state", e);
            return;
        }

        // process all found videos
        for (final Video video : videos) {
            // lock the video for processing
            if (!this.acquireLockInState(video, State.CHECK)) {
                LOG.debug("unable to get lock for video {}", video.getId());
                continue;
            }

            try {
                // get the needed output types
                final Set<Type> types = this.getNeededOutputTypes(video);

                // we have no needed types, so mark video as encoded
                if (types.isEmpty()) {
                    txService.inTransaction(new Runnable() {
                        @Override
                        public void run() {
                            final Video fresh = manager.refresh(video);
                            fresh.setState(State.ENCODED);
                        }
                    });
                }
                // there are still outputs to be generated
                else {
                    final String jobId = aws.enqueueEncodingJob(video, types);
                    if (jobId != null) {
                        txService.inTransaction(new Runnable() {
                            @Override
                            public void run() {
                                final Video fresh = manager.refresh(video);

                                // add enqueued encoding job
                                fresh.setState(State.ENCODING);
                                fresh.addJob(new AwsJob(jobId));
                            }
                        });
                    }
                }
            } catch (final Exception ignored) {
            } finally {
                // release the video lock
                try {
                    this.releaseLock(video);
                } catch (final Exception e) {
                    // log exception, but don't propagate it
                    LOG.error("error trying to clear video lock", e);
                }
            }
        }
    }

    private Set<Type> getNeededOutputTypes(final Video orig) {
        try {
            return this.txService.inReadOnlyTransaction(new Callable<Set<Type>>() {
                @Override
                public Set<Type> call() throws Exception {
                    final Video video = manager.refresh(orig);

                    // determine the required types that are still needed
                    final EnumSet<Type> types = EnumSet.noneOf(Type.class);
                    for (final Type type : AwsOutput.REQUIRED_TYPES) {
                        final AwsOutput output = video.getOutput(type);
                        if (output == null || output.isStale()) {
                            types.add(type);
                        }
                    }

                    return types;
                }
            });
        } catch (final Exception e) {
            return null;
        }
    }

    private boolean acquireLock(final Video video) {
        return this.acquireLockInState(video, null);
    }

    private boolean acquireLockInState(final Video video, final State state) {
        // try acquiring the lock 3 times (retry for tx errors)
        for (int i = 0; i < 3; i++) {
            try {
                return this.txService.inTransaction(new Callable<Boolean>() {
                    @Override
                    public Boolean call() throws Exception {
                        final Video fresh = manager.refresh(video, LockModeType.PESSIMISTIC_WRITE);

                        // ensure the video is in the correct state
                        if (state != null && !fresh.isInState(state)) {
                            return false;
                        }

                        // try getting the lock
                        return fresh.getLock();
                    }
                });
            } catch (final Exception ignored) {
            }
        }

        return false;
    }

    private void releaseLock(final Video video) {
        // try acquiring the lock 3 times (retry for tx errors)
        for (int i = 0; i < 3; i++) {
            try {
                this.txService.inTransaction(new Runnable() {
                    @Override
                    public void run() {
                        final Video fresh = manager.refresh(video, LockModeType.PESSIMISTIC_WRITE);
                        fresh.releaseLock();
                    }
                });

                break;
            } catch (final PersistenceException ignored) {
            }
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    private void delete(final AwsFileToUpload upload, final boolean deleteFile) {
        if (deleteFile) {
            aws.delete(upload.getFile());
        }

        this.em.remove(upload);
    }
}
