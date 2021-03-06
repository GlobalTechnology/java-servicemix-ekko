package org.ccci.gto.servicemix.ekko.cloudvideo;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
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
import org.ccci.gto.servicemix.ekko.cloudvideo.model.Video.VideoQuery;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleTrigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;

public class VideoStateMachine {
    private static final Logger LOG = LoggerFactory.getLogger(VideoStateMachine.class);

    private static final String TRIGGERS_GROUP = "VideoStateMachine_TRIGGERS";

    private static final int UPLOADS_SLICE_SIZE = 100;
    private static final int CHECK_ENCODES_SLICE_SIZE = 100;
    private static final int OLD_ENCODES_SLICE_SIZE = 100;

    private static final long DEFAULT_OLD_JOB_CHECK_AGE = 6 * 60 * 60 * 1000;

    @PersistenceContext
    private EntityManager em;

    @Autowired
    private TransactionService txService;

    @Autowired
    private VideoManager manager;

    @Autowired(required = false)
    private SchedulerFactoryBean scheduler;

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
                    final AwsFileToUpload upload = new AwsFileToUpload(manager.getManaged(video), file);
                    upload.setDeleteSource(deleteAfterUpload);
                    em.persist(upload);
                }
            });
        } catch (final Exception e) {
            LOG.error("error enqueuing upload", e);
            return false;
        }

        // process pending uploads
        this.scheduleProcessPendingUploads();

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
        boolean checkOutputs = false;
        for (final Video video : pending) {
            // lock the video for processing
            if (!this.acquireLock(video, State.NEW, State.CHECK, State.ENCODING, State.ENCODED)) {
                continue;
            }

            try {
                // retrieve the most recent upload request, deleting all stale ones in the process
                final AwsFileToUpload upload = this.txService.inTransaction(new Callable<AwsFileToUpload>() {
                    @Override
                    public AwsFileToUpload call() throws Exception {
                        final TypedQuery<AwsFileToUpload> query = em.createNamedQuery("AwsFileToUpload.pendingUploads",
                                AwsFileToUpload.class);
                        query.setParameter("video", manager.getManaged(video));
                        final List<AwsFileToUpload> files = query.getResultList();

                        // get the latest upload for this video
                        final AwsFileToUpload upload = files.size() > 0 ? files.get(files.size() - 1) : null;

                        // don't process any uploads other than the most recent one
                        if (files.size() > 0) {
                            final Collection<AwsFile> protectedFile = Collections.singleton(upload != null ? upload
                                    .getFile() : null);
                            for (final AwsFileToUpload staleUpload : files.subList(0, files.size() - 1)) {
                                manager.delete(staleUpload, protectedFile);
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
                                manager.delete(em.merge(upload), null);
                            }
                        });

                        // cancel any stale encoding jobs
                        aws.cleanupStaleEncodingJobs(video);

                        // check for missing outputs
                        checkOutputs = true;
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

        // do we need to start encodes?
        if (checkOutputs) {
            this.scheduleCheckForMissingOutputs();
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
            if (!this.acquireLock(video, State.CHECK)) {
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
                            final Video fresh = manager.getManaged(video);
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
                                final Video fresh = manager.getManaged(video);

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
                    final Video video = manager.getManaged(orig);

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

    public void checkEncodingJob(final String jobId) {
        // lookup video containing specified job
        final Video video;
        try {
            video = this.txService.inReadOnlyTransaction(new Callable<Video>() {
                @Override
                public Video call() throws Exception {
                    final TypedQuery<Video> query = em.createNamedQuery("Video.findByJobId", Video.class);
                    query.setParameter("jobId", jobId);
                    query.setMaxResults(1);
                    final List<Video> videos = query.getResultList();
                    return videos != null && videos.size() > 0 ? videos.get(0) : null;
                }
            });
        } catch (final Exception e) {
            LOG.debug("Error fetching video", e);
            return;
        }
        if (video == null) {
            LOG.debug("video for specified jobId not found");
            return;
        }

        // lock the video for processing
        if (!this.acquireLock(video, State.ENCODING, State.DELETED)) {
            LOG.debug("unable to get lock for video {}", video.getId());
            return;
        }

        boolean checkOutputs = false;
        try {
            // check the state of the specified job
            aws.checkEncodingJob(video, jobId);

            // finish encoding
            this.finishEncoding(video);
            checkOutputs = true;
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

        // check for missing outputs
        if (checkOutputs) {
            this.scheduleCheckForMissingOutputs();
        }
    }

    public void checkOldEncodingJobs() {
        final Date oldDate = new Date(System.currentTimeMillis() - DEFAULT_OLD_JOB_CHECK_AGE);

        // fetch a list of old jobs that are still "encoding"
        final List<Video> encoding;
        try {
            encoding = this.txService.inReadOnlyTransaction(new Callable<List<Video>>() {
                @Override
                public List<Video> call() throws Exception {
                    final TypedQuery<Video> query = em.createNamedQuery("Video.findByOldJobs", Video.class);
                    query.setParameter("date", oldDate);
                    query.setMaxResults(OLD_ENCODES_SLICE_SIZE);
                    return query.getResultList();
                }
            });
        } catch (final Exception e) {
            LOG.error("error retrieving videos with old encoding jobs", e);
            return;
        }

        // process all found videos
        boolean checkOutputs = false;
        for (final Video video : encoding) {
            // lock the video for processing
            if (!this.acquireLock(video, State.ENCODING, State.DELETED)) {
                LOG.debug("unable to get lock for video {}", video.getId());
                continue;
            }

            try {
                // find jobs for this video
                final List<AwsJob> jobs = this.txService.inReadOnlyTransaction(new Callable<List<AwsJob>>() {
                    @Override
                    public List<AwsJob> call() throws Exception {
                        final Video fresh = manager.getManaged(video);
                        return fresh != null ? fresh.getJobs() : Collections.<AwsJob> emptyList();
                    }
                });

                // check any jobs that haven't been checked recently
                for (final AwsJob job : jobs) {
                    if (job.getLastChecked().before(oldDate)) {
                        aws.checkEncodingJob(video, job.getId());
                    }
                }

                // finish encoding
                this.finishEncoding(video);

                // check for missing outputs
                checkOutputs = true;
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

        // should we check for any missing outputs
        if (checkOutputs) {
            this.scheduleCheckForMissingOutputs();
        }
    }

    public boolean deleteVideo(final Video video) {
        // short-circuit if we don't have a video object
        if (video == null) {
            return false;
        }

        // start deleting the video
        final boolean deleted = this.manager.preDelete(video);

        // trigger cleanup of videos being deleted
        if (deleted) {
            this.scheduleCleanupDeletedVideos();
        }

        return deleted;
    }

    public void cleanupDeletedVideos() {
        // fetch a list of videos being deleted
        final List<Video> deleted = this.manager.getVideos(new VideoQuery().deleted(true));

        for (final Video video : deleted) {
            // lock the video for processing
            if (!this.acquireLock(video)) {
                continue;
            }

            try {
                // cleanup stale encoding jobs
                aws.cleanupStaleEncodingJobs(video);

                // update video object
                this.txService.inTransaction(new Runnable() {
                    @Override
                    public void run() {
                        final Video fresh = manager.getManaged(video, LockModeType.PESSIMISTIC_WRITE);

                        // move video to DELETED state
                        fresh.setState(State.DELETED);

                        // cancel any pending uploads
                        final TypedQuery<AwsFileToUpload> query = em.createNamedQuery("AwsFileToUpload.pendingUploads",
                                AwsFileToUpload.class);
                        query.setParameter("video", fresh);
                        for (final AwsFileToUpload upload : query.getResultList()) {
                            manager.delete(upload, null);
                        }
                    }
                });

                // delete the video
                this.manager.delete(video);
            } catch (final Exception e) {
                // log the error, but don't break processing of the next video
                LOG.debug("cleanupDeletedVideos() error", e);
            } finally {
                // release the video lock
                try {
                    this.releaseLock(video);
                } catch (final Exception e) {
                    // unexpected exception while releasing lock, log but don't propagate
                    LOG.error("exception thrown while releasing video lock", e);
                }
            }
        }
    }

    private boolean acquireLock(final Video video, final State... states) {
        // try acquiring the lock 3 times (retry for tx errors)
        for (int i = 0; i < 3; i++) {
            try {
                return this.txService.inTransaction(new Callable<Boolean>() {
                    @Override
                    public Boolean call() throws Exception {
                        final Video fresh = manager.getManaged(video, LockModeType.PESSIMISTIC_WRITE);

                        // ensure the video is in one of the specified states
                        boolean validState = states.length == 0;
                        for (final State state : states) {
                            if (fresh.isInState(state)) {
                                validState = true;
                                break;
                            }
                        }
                        if (!validState) {
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
                        final Video fresh = manager.getManaged(video, LockModeType.PESSIMISTIC_WRITE);
                        if (fresh != null) {
                            fresh.releaseLock();
                        }
                    }
                });

                break;
            } catch (final PersistenceException ignored) {
            }
        }
    }

    private void finishEncoding(final Video orig) {
        try {
            this.txService.inTransaction(new Runnable() {
                @Override
                public void run() {
                    final Video video = manager.getManaged(orig);
                    if (video.isInState(State.ENCODING) && video.getJobs().size() == 0) {
                        video.setState(State.CHECK);
                    }
                }
            });
        } catch (final Exception ignore) {
        }
    }

    private void scheduleProcessPendingUploads() {
        this.scheduleJob("processPendingUploads", 0);
    }

    private void scheduleCheckForMissingOutputs() {
        this.scheduleJob("checkForMissingOutputs", 0);
    }

    private void scheduleCleanupDeletedVideos() {
        this.scheduleJob("cleanupDeletedVideos", 0);
    }

    private void scheduleJob(final String name, final long delay) {
        final Scheduler scheduler;
        if (this.scheduler != null && (scheduler = this.scheduler.getScheduler()) != null) {
            try {
                // schedule a new trigger if one doesn't exist already
                if (scheduler.getTrigger(name, TRIGGERS_GROUP) == null) {
                    scheduler.scheduleJob(new SimpleTrigger(name, TRIGGERS_GROUP, name, Scheduler.DEFAULT_GROUP,
                            new Date(System.currentTimeMillis() + delay), null, 0, 0));
                }
            } catch (SchedulerException e) {
                LOG.debug("error triggering {}", name, e);
            }
        } else {
            LOG.debug("no scheduler present");
        }
    }
}
