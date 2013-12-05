package org.ccci.gto.servicemix.ekko.cloudvideo;

import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;

import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import javax.persistence.PersistenceContext;
import javax.persistence.PersistenceException;
import javax.persistence.TypedQuery;

import org.apache.commons.lang.StringUtils;
import org.ccci.gto.persistence.tx.TransactionService;
import org.ccci.gto.servicemix.ekko.cloudvideo.model.AwsFile;
import org.ccci.gto.servicemix.ekko.cloudvideo.model.AwsFileToDelete;
import org.ccci.gto.servicemix.ekko.cloudvideo.model.AwsFileToUpload;
import org.ccci.gto.servicemix.ekko.cloudvideo.model.Video;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleTrigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public class AwsJobController {
    private static final Logger LOG = LoggerFactory.getLogger(AwsJobController.class);

    private static final String TRIGGERS_GROUP = "AwsJobController_TRIGGERS";

    private static final int UPLOADS_SLICE_SIZE = 100;

    @Autowired
    private AwsVideoManager manager;

    @PersistenceContext
    protected EntityManager em;

    @Autowired
    private TransactionService txService;

    @Autowired(required = false)
    private SchedulerFactoryBean scheduler;

    public void processUploads() {
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
                // retrieve the most recent upload request, deleting all stale
                // ones in the process
                final AwsFileToUpload upload = this.txService.inTransaction(new Callable<AwsFileToUpload>() {
                    @Override
                    public AwsFileToUpload call() throws Exception {
                        final TypedQuery<AwsFileToUpload> query = em.createNamedQuery("AwsFileToUpload.pendingUploads",
                                AwsFileToUpload.class);
                        query.setParameter("video", manager.refresh(video));
                        final List<AwsFileToUpload> files = query.getResultList();

                        // get the latest upload for this video
                        final AwsFileToUpload upload = files.size() > 0 ? files.get(files.size() - 1) : null;

                        // don't process any requests other than the most recent
                        // one
                        if (files.size() > 1) {
                            final AwsFile file = upload.getFile();
                            for (final AwsFileToUpload staleUpload : files.subList(0, files.size() - 1)) {
                                // should we delete the AwsFile?
                                if (staleUpload.isDeleteSource()) {
                                    // make sure this doesn't delete the upload
                                    // that will actually be processed
                                    final AwsFile staleFile = staleUpload.getFile();
                                    if (file == null || !file.exists() || staleFile == null
                                            || !StringUtils.equals(file.getBucket(), staleFile.getBucket())
                                            || !StringUtils.equals(file.getKey(), staleFile.getKey())
                                            || !StringUtils.equals(file.getVersion(), staleFile.getVersion())) {
                                        em.persist(new AwsFileToDelete(staleFile));
                                    }
                                }

                                // remove the stale upload
                                em.remove(staleUpload);
                            }
                        }

                        // return the upload being processed
                        return upload;
                    }
                });

                // is there an upload to process?
                if (upload != null) {
                    final boolean success = this.manager.updateMaster(video, upload.getFile());

                    // we successfully moved the upload, so cleanup the item in
                    // the queue
                    if (success) {
                        txService.inTransaction(new Runnable() {
                            @Override
                            public void run() {
                                cleanup(em.merge(upload), upload.isDeleteSource());
                            }
                        });
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

            // schedule processDeletions
            this.scheduleProcessDeletions();
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    private void cleanup(final AwsFileToUpload upload, final boolean deleteFile) {
        if (deleteFile) {
            this.em.persist(new AwsFileToDelete(upload.getFile()));
        }
        this.em.remove(upload);
    }

    public void processDeletions() {
        // TODO
    }

    private boolean acquireLock(final Video video) {
        // try acquiring the lock 3 times (retry for tx errors)
        for (int i = 0; i < 3; i++) {
            try {
                return this.txService.inTransaction(new Callable<Boolean>() {
                    @Override
                    public Boolean call() throws Exception {
                        final Video fresh = manager.refresh(video, LockModeType.PESSIMISTIC_WRITE);
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

    public void scheduleProcessUploads() {
        this.scheduleJob("processUploads", 10000);
    }

    public void scheduleProcessDeletions() {
        this.scheduleJob("processDeletions", 60000);
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
