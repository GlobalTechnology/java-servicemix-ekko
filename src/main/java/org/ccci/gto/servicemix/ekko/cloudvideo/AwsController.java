package org.ccci.gto.servicemix.ekko.cloudvideo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashSet;
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
import org.ccci.gto.servicemix.ekko.cloudvideo.model.AwsFileToDelete;
import org.ccci.gto.servicemix.ekko.cloudvideo.model.AwsFileToUpload;
import org.ccci.gto.servicemix.ekko.cloudvideo.model.AwsJob;
import org.ccci.gto.servicemix.ekko.cloudvideo.model.AwsOutput;
import org.ccci.gto.servicemix.ekko.cloudvideo.model.AwsOutput.Type;
import org.ccci.gto.servicemix.ekko.cloudvideo.model.Video;
import org.ccci.gto.servicemix.ekko.cloudvideo.model.Video.State;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleTrigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.amazonaws.services.elastictranscoder.AmazonElasticTranscoder;
import com.amazonaws.services.elastictranscoder.model.CancelJobRequest;
import com.amazonaws.services.elastictranscoder.model.CreateJobOutput;
import com.amazonaws.services.elastictranscoder.model.CreateJobRequest;
import com.amazonaws.services.elastictranscoder.model.CreateJobResult;
import com.amazonaws.services.elastictranscoder.model.Job;
import com.amazonaws.services.elastictranscoder.model.JobInput;
import com.amazonaws.services.elastictranscoder.model.JobOutput;
import com.amazonaws.services.elastictranscoder.model.ReadJobRequest;
import com.amazonaws.services.elastictranscoder.model.ReadJobResult;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.CopyObjectRequest;
import com.amazonaws.services.s3.model.CopyObjectResult;
import com.amazonaws.services.s3.model.DeleteObjectsRequest;
import com.amazonaws.services.s3.model.DeleteObjectsResult;
import com.amazonaws.services.s3.model.DeleteObjectsResult.DeletedObject;
import com.amazonaws.services.s3.model.MultiObjectDeleteException;

public class AwsController {
    private static final Logger LOG = LoggerFactory.getLogger(AwsController.class);

    private static final String TRIGGERS_GROUP = "AwsJobController_TRIGGERS";

    private static final int DELETIONS_BUCKETS_SLICE_SIZE = 1;
    private static final int DELETIONS_FILES_SLICE_SIZE = 1000;
    private static final int UPLOADS_SLICE_SIZE = 100;
    private static final int CHECK_ENCODES_SLICE_SIZE = 100;
    private static final int START_ENCODE_SLICE_SIZE = 100;
    private static final int OLD_ENCODES_SLICE_SIZE = 100;

    @Autowired(required = false)
    private SchedulerFactoryBean scheduler;

    @PersistenceContext
    protected EntityManager em;

    @Autowired
    private TransactionService txService;

    @Autowired
    private VideoManager manager;

    private AmazonS3 s3 = null;
    private AmazonElasticTranscoder transcoder = null;

    /* AWS configuration */
    private String awsETPipelineId;
    private String awsS3BucketMasters;
    private String awsS3BucketEncoded;
    private String awsS3KeyPrefix = null;

    /* setters & getters */
    public final void setS3(final AmazonS3 s3) {
        this.s3 = s3;
    }

    public final void setTranscoder(final AmazonElasticTranscoder transcoder) {
        this.transcoder = transcoder;
    }

    public final void setAwsETPipelineId(final String awsETPipelineId) {
        this.awsETPipelineId = awsETPipelineId;
    }

    public final void setAwsS3BucketMasters(final String bucket) {
        this.awsS3BucketMasters = bucket;
    }

    public final void setAwsS3BucketEncoded(final String awsS3BucketEncoded) {
        this.awsS3BucketEncoded = awsS3BucketEncoded;
    }

    public final void setAwsS3KeyPrefix(final String prefix) {
        this.awsS3KeyPrefix = prefix;
    }

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
                    final boolean success = updateMaster(video, upload.getFile());

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
                        cleanupStaleEncodingJobs(video);

                        // schedule starting the pending encodes
                        this.scheduleProcessStartEncodes();
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

    public void processStartEncodes() {
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
                    this.createEncodingJob(video, types);
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

    public void processOldEncodingJobs() {
        final Date oldDate = new Date(System.currentTimeMillis() - (24 * 60 * 60 * 1000));

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
        for (final Video video : encoding) {
            // lock the video for processing
            if (!this.acquireLockInState(video, State.ENCODING)) {
                LOG.debug("unable to get lock for video {}", video.getId());
                continue;
            }

            try {
                // find jobs for this video
                final List<AwsJob> jobs = this.txService.inReadOnlyTransaction(new Callable<List<AwsJob>>() {
                    @Override
                    public List<AwsJob> call() throws Exception {
                        final Video fresh = manager.refresh(video);
                        return fresh != null ? fresh.getJobs() : Collections.<AwsJob> emptyList();
                    }
                });

                // check any jobs that haven't been checked recently
                for (final AwsJob job : jobs) {
                    if (job.getLastChecked().before(oldDate)) {
                        this.checkEncodingJob(video, job.getId());
                    }
                }

                // finish encoding
                this.finishEncoding(video);
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

    public void processDeletions() {
        // fetch a list of buckets with files to be deleted
        final List<String> buckets;
        try {
            buckets = this.txService.inReadOnlyTransaction(new Callable<List<String>>() {
                @Override
                public List<String> call() throws Exception {
                    final TypedQuery<String> query = em.createNamedQuery("AwsFileToDelete.bucketsWithPendingDeletions",
                            String.class);
                    query.setMaxResults(DELETIONS_BUCKETS_SLICE_SIZE);
                    return query.getResultList();
                }
            });
        } catch (final Exception e) {
            LOG.error("error retrieving buckets with pending deletions for processDeletions", e);
            return;
        }

        // delete all files in deletion queue
        for (final String bucket : buckets) {
            // fetch a list of files to delete in this bucket
            final List<AwsFileToDelete> items;
            try {
                items = this.txService.inReadOnlyTransaction(new Callable<List<AwsFileToDelete>>() {
                    @Override
                    public List<AwsFileToDelete> call() throws Exception {
                        final TypedQuery<AwsFileToDelete> query = em.createNamedQuery(
                                "AwsFileToDelete.pendingDeletionsForBucket", AwsFileToDelete.class);
                        query.setParameter("bucket", bucket);
                        query.setMaxResults(DELETIONS_FILES_SLICE_SIZE);
                        return query.getResultList();
                    }
                });
            } catch (final Exception e) {
                LOG.error("error retrieving files to be delete in bucket {}", bucket, e);
                continue;
            }

            // generate list of keys to be deleted from this bucket
            final List<String> keys = new ArrayList<>();
            for (final AwsFileToDelete item : items) {
                final AwsFile file = item.getFile();
                if (file != null && file.exists()) {
                    keys.add(file.getKey());
                }
            }

            // issue batch deletion request
            List<DeletedObject> deleted = null;
            try {
                final DeleteObjectsResult result = this.s3.deleteObjects(new DeleteObjectsRequest(bucket).withKeys(keys
                        .toArray(new String[keys.size()])));
                deleted = result.getDeletedObjects();
            } catch (final MultiObjectDeleteException e) {
                deleted = e.getDeletedObjects();
            } catch (final Exception e) {
                LOG.error("Unhandled exception in S3->deleteObjects", e);
                continue;
            }

            // process response in set of files deleted
            final Set<AwsFile> files = new HashSet<>();
            for (final DeletedObject object : deleted) {
                files.add(new AwsFile(bucket, object.getKey()));
            }

            // remove queued deletions for all files that were successfully deleted
            this.txService.inTransaction(new Runnable() {
                @Override
                public void run() {
                    for (final AwsFileToDelete item : items) {
                        if (files.contains(item.getFile())) {
                            em.remove(em.merge(item));
                        }
                    }
                }
            });
        }
    }

    private boolean updateMaster(final Video orig, final AwsFile source) {
        // short-circuit if we don't have a video object or source video
        if (orig == null || source == null || !source.exists()) {
            return false;
        }

        // move new master video
        final CopyObjectRequest request = new CopyObjectRequest(source.getBucket(), source.getKey(),
                this.awsS3BucketMasters, awsKeyPrefix(orig) + extractName(source.getKey()));
        final CopyObjectResult result;
        try {
            result = s3.copyObject(request);
        } catch (final Exception e) {
            LOG.error("error trying to copy resource between S3 buckets", e);
            return false;
        }

        // short-circuit if we don't have a valid result
        if (result == null) {
            return false;
        }

        // create AwsFile object for new master video
        final AwsFile master = new AwsFile(request.getDestinationBucketName(), request.getDestinationKey());

        // update video object with new master
        try {
            txService.inTransaction(new Runnable() {
                @Override
                public void run() {
                    final Video video = manager.refresh(orig);

                    // delete previous master
                    delete(video.getMaster());

                    // store new master
                    video.setMaster(master);

                    // mark any pending encodes as stale
                    for (final AwsJob job : video.getJobs()) {
                        job.setStale(true);
                    }

                    // mark old thumbnail as stale
                    video.setStaleThumbnail(true);

                    // mark old outputs as stale
                    for (final AwsOutput output : video.getOutputs()) {
                        output.setStale(true);
                    }

                    // transition to the CHECK state
                    video.setState(State.CHECK);
                }
            });
        } catch (final Exception e1) {
            // we delete the orphaned AwsFile that wasn't correctly recorded
            LOG.debug("error recording new master", e1);
            try {
                txService.inTransaction(new Runnable() {
                    @Override
                    public void run() {
                        LOG.debug("trying to clean up orphaned AwsFile {}", master);
                        delete(master);
                    }
                });
            } catch (final Exception e2) {
                LOG.error("error cleaning up orphaned AwsFile after exception", e2);
            }
            return false;
        }

        // return success
        return true;
    }

    private void cleanupStaleEncodingJobs(final Video orig) {
        if (orig == null) {
            return;
        }

        // get a list of stale encoding jobs
        final List<AwsJob> jobs;
        try {
            jobs = txService.inReadOnlyTransaction(new Callable<List<AwsJob>>() {
                @Override
                public List<AwsJob> call() throws Exception {
                    final Video video = manager.refresh(orig);
                    final List<AwsJob> jobs = new ArrayList<>();
                    for (final AwsJob job : video.getJobs()) {
                        if (job.isStale()) {
                            jobs.add(job);
                        }
                    }

                    return jobs;
                }
            });
        } catch (final Exception e) {
            return;
        }

        // short-circuit if we don't have any stale jobs
        if (jobs == null || jobs.isEmpty()) {
            return;
        }

        // try canceling stale jobs
        final List<AwsJob> canceledJobs = new ArrayList<>();
        for (final AwsJob job : jobs) {
            try {
                this.transcoder.cancelJob(new CancelJobRequest().withId(job.getId()));

                // we assume success at canceling the job if no exception was
                // thrown
                canceledJobs.add(job);
            } catch (final Exception e) {
                // job was processed or is processing, do nothing
            }
        }

        // remove all jobs that were canceled
        this.txService.inTransaction(new Runnable() {
            @Override
            public void run() {
                final Video video = manager.refresh(orig);
                for (final AwsJob job : canceledJobs) {
                    video.removeJob(job.getId());
                }
            }
        });
    }

    private void createEncodingJob(final Video orig, final Collection<Type> types) {
        // short-circuit if we don't have a valid video object
        if (orig == null) {
            return;
        }

        // create encode job request
        final CreateJobRequest request;
        try {
            request = txService.inTransaction(new Callable<CreateJobRequest>() {
                @Override
                public CreateJobRequest call() throws Exception {
                    final Video video = manager.refresh(orig);
                    final AwsFile master = video.getMaster();
                    if (master != null && master.exists()) {
                        try {
                            final Set<Type> existingOutputs = new HashSet<>();
                            for (final AwsOutput output : video.getOutputs()) {
                                if (output != null && !output.isStale()) {
                                    existingOutputs.add(output.getType());
                                }
                            }

                            // create job outputs
                            final List<CreateJobOutput> outputs = new ArrayList<>();
                            for (final Type type : types) {
                                if (!existingOutputs.contains(type)) {
                                    final CreateJobOutput output = createJobOutput(type, master);
                                    if (output != null) {
                                        outputs.add(output);
                                    }
                                }
                            }

                            if (outputs.size() > 0) {
                                // outputs, so return a CreateJobRequest
                                return new CreateJobRequest().withPipelineId(awsETPipelineId)
                                        .withInput(new JobInput().withKey(master.getKey()))
                                        .withOutputKeyPrefix(awsKeyPrefix(video)).withOutputs(outputs);
                            } else {
                                // no outputs, so no request needed
                                return null;
                            }
                        } catch (final Exception e) {
                            throw e;
                        }
                    }

                    return null;
                }
            });
        } catch (final Exception e) {
            return;
        }

        // no encode request, return false
        if (request == null) {
            return;
        }

        // TODO: try creating it 3 times (with a delay between attempts)
        final CreateJobResult result = this.transcoder.createJob(request);

        // update video object with enqueue job pointer
        txService.inTransaction(new Runnable() {
            @Override
            public void run() {
                final Video video = manager.refresh(orig);

                // record encoding job(s)
                video.addJob(new AwsJob(result.getJob().getId()));

                // update video state
                video.setState(State.ENCODING);
            }
        });

        return;
    }

    private boolean checkEncodingJob(final Video orig, final String jobId) {
        // short-circuit if we don't have a valid video object and job id
        if (orig == null || jobId == null) {
            return false;
        }

        // fetch the job info from AWS
        final Job etJob;
        try {
            final ReadJobResult result = this.transcoder.readJob(new ReadJobRequest().withId(jobId));
            etJob = result.getJob();
        } catch (final Exception e) {
            LOG.debug("error retrieving job from AWS", e);

            // unexpected exception, return false
            return false;
        }

        // update Video record with current state of Job
        try {
            return this.txService.inTransaction(new Callable<Boolean>() {
                @Override
                public Boolean call() {
                    // find the job in the DB
                    final Video video = manager.refresh(orig);
                    final AwsJob job = video.getJob(jobId);
                    if (job != null) {
                        // update last checked time
                        job.updateLastChecked();

                        // process based on status
                        final String status = etJob.getStatus();
                        if (status != null) {
                            final String keyPrefix = etJob.getOutputKeyPrefix();
                            switch (status) {
                            case "Complete":
                                // only record complete jobs that are not stale
                                if (!job.isStale()) {
                                    for (final JobOutput jobOutput : etJob.getOutputs()) {
                                        // generate AwsOutput object
                                        final Type type = Type.fromPreset(jobOutput.getPresetId());
                                        final AwsOutput output = new AwsOutput(video, type);
                                        output.setFile(new AwsFile(awsS3BucketEncoded, keyPrefix + jobOutput.getKey()));

                                        // remove old output (protecting files that are in the new output)
                                        final AwsOutput oldOutput = video.getOutput(type);
                                        if (oldOutput != null) {
                                            final Set<AwsFile> protectedFiles = new HashSet<>();
                                            protectedFiles.add(output.getFile());
                                            protectedFiles.addAll(output.getFiles());
                                            delete(oldOutput, protectedFiles);
                                            em.flush();
                                        }

                                        // save the new output
                                        em.persist(output);
                                    }
                                }
                            case "Canceled":
                            case "Error":
                                // delete all potential output files if the job is stale or was canceled
                                if (job.isStale() || "Canceled".equals(status)) {
                                    // generate the set of files to delete
                                    final Set<AwsFile> files = new HashSet<>();
                                    for (final JobOutput jobOutput : etJob.getOutputs()) {
                                        files.add(new AwsFile(awsS3BucketEncoded, keyPrefix + jobOutput.getKey()));
                                    }

                                    // prevent deletion of any files currently being used
                                    for (final AwsOutput output : video.getOutputs()) {
                                        files.remove(output.getFile());
                                        files.removeAll(output.getFiles());
                                    }

                                    // delete any remaining files
                                    for (final AwsFile file : files) {
                                        delete(file);
                                    }
                                }

                                // remove job if it isn't in an error state or is stale
                                if (!"Error".equals(status) || job.isStale()) {
                                    video.removeJob(job);
                                }

                                break;
                            case "Submitted":
                            case "Progressing":
                                // do nothing since we are still processing
                            default:
                                return false;
                            }
                        }
                    }

                    return false;
                }
            });
        } catch (Exception e) {
            LOG.debug("updating output error", e);
            return false;
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

    private void finishEncoding(final Video orig) {
        try {
            this.txService.inTransaction(new Runnable() {
                @Override
                public void run() {
                    final Video video = manager.refresh(orig);
                    if (video.isInState(State.ENCODING) && video.getJobs().size() == 0) {
                        video.setState(State.CHECK);
                    }
                }
            });
        } catch (final Exception ignore) {
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

    private static String extractName(final String key) {
        assert key != null : "key cannot be null";

        // extract a file name
        final int i = key.lastIndexOf("/");
        if (i >= 0 && i < key.length() - 1) {
            return key.substring(i + 1);
        }

        return key;
    }

    private static CreateJobOutput createJobOutput(final Type type, final AwsFile master) {
        final CreateJobOutput output = new CreateJobOutput().withPresetId(type.preset);

        // find the extension for the output
        final String ext;
        switch (type) {
        case MP4_480P_16_9:
            ext = "mp4";
            break;
        case HLS_1M:
            ext = "ts";
            break;
        case UNKNOWN:
        default:
            return null;
        }

        // generate the key for this output
        String name = extractName(master.getKey());
        final int i = name.lastIndexOf(".");
        if (i >= 0 && i < name.length() - 1) {
            name = name.substring(0, i);
        }
        output.setKey("output/" + type + "/" + name + "." + ext);

        // return the generated output
        return output;
    }

    private String awsKeyPrefix(final Video video) {
        return (this.awsS3KeyPrefix != null ? this.awsS3KeyPrefix : "") + Long.valueOf(video.getId()).toString() + "/"
                + Long.valueOf(video.getVersion()).toString() + "/";
    }

    @Transactional(propagation = Propagation.MANDATORY)
    private void delete(final Collection<AwsFile> files) {
        if (files != null) {
            for (final AwsFile file : files) {
                delete(file);
            }
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    private void delete(final AwsFile file) {
        if (file != null && file.exists()) {
            em.persist(new AwsFileToDelete(file));
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    private void delete(final AwsOutput output) {
        delete(output, Collections.<AwsFile> emptySet());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    private void delete(final AwsOutput output, final Collection<AwsFile> protectedFiles) {
        // get all the files that need to be deleted
        final Set<AwsFile> files = new HashSet<>();
        files.add(output.getFile());
        files.addAll(output.getFiles());

        // protected the specified files
        if (protectedFiles != null) {
            files.removeAll(protectedFiles);
        }

        // delete all AwsFiles
        delete(files);

        // delete AwsOutput
        this.em.remove(output);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    private void delete(final AwsFileToUpload upload, final boolean deleteFile) {
        if (deleteFile) {
            delete(upload.getFile());
        }

        this.em.remove(upload);
    }

    public void scheduleProcessUploads() {
        this.scheduleJob("processUploads", 10000);
    }

    public void scheduleProcessStartEncodes() {
        this.scheduleJob("processStartEncodes", 5000);
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
