package org.ccci.gto.servicemix.ekko.cloudvideo;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;

import org.apache.commons.lang.StringUtils;
import org.ccci.gto.hls.m3u.model.Element;
import org.ccci.gto.hls.m3u.model.Media;
import org.ccci.gto.hls.m3u.model.Playlist;
import org.ccci.gto.hls.m3u.parser.PlaylistParser;
import org.ccci.gto.persistence.tx.TransactionService;
import org.ccci.gto.servicemix.ekko.cloudvideo.model.AwsFile;
import org.ccci.gto.servicemix.ekko.cloudvideo.model.AwsFileToDelete;
import org.ccci.gto.servicemix.ekko.cloudvideo.model.AwsJob;
import org.ccci.gto.servicemix.ekko.cloudvideo.model.AwsOutput;
import org.ccci.gto.servicemix.ekko.cloudvideo.model.AwsOutput.Type;
import org.ccci.gto.servicemix.ekko.cloudvideo.model.HlsSegment;
import org.ccci.gto.servicemix.ekko.cloudvideo.model.Video;
import org.ccci.gto.servicemix.ekko.cloudvideo.model.Video.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;

public class AwsVideoController {
    private static final Logger LOG = LoggerFactory.getLogger(AwsVideoController.class);

    private static final int DELETIONS_BUCKETS_SLICE_SIZE = 10;
    private static final int DELETIONS_FILES_SLICE_SIZE = 1000;

    private static final long DEFAULT_PRESIGNED_URL_MIN_AGE = 6 * 60 * 60 * 1000;

    @PersistenceContext
    private EntityManager em;

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

    boolean updateMaster(final Video orig, final AwsFile source) {
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
                    final Video video = manager.getManaged(orig);

                    // delete previous master
                    manager.delete(video.getMaster());

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
                        manager.delete(master);
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

    void cleanupStaleEncodingJobs(final Video orig) {
        if (orig == null) {
            return;
        }

        // get a list of stale encoding jobs
        final List<AwsJob> jobs;
        try {
            jobs = txService.inReadOnlyTransaction(new Callable<List<AwsJob>>() {
                @Override
                public List<AwsJob> call() throws Exception {
                    final Video video = manager.getManaged(orig);
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
                final Video video = manager.getManaged(orig);
                for (final AwsJob job : canceledJobs) {
                    video.removeJob(job.getId());
                }
            }
        });
    }

    String enqueueEncodingJob(final Video orig, final Collection<Type> types) {
        // short-circuit if we don't have a valid video object or any types
        if (orig == null || types == null || types.isEmpty()) {
            return null;
        }

        // short-circuit if we don't have a master video
        final AwsFile master = orig.getMaster();
        if (master == null || !master.exists()) {
            return null;
        }

        // create requested job outputs
        final List<CreateJobOutput> outputs = new ArrayList<>();
        for (final Type type : types) {
            final CreateJobOutput output = createJobOutput(type, master);
            if (output != null) {
                outputs.add(output);
            }
        }

        // short-circuit if we don't have any outputs
        if (outputs.isEmpty()) {
            return null;
        }

        // create encode job request
        final CreateJobRequest request = new CreateJobRequest().withPipelineId(awsETPipelineId)
                .withInput(new JobInput().withKey(master.getKey())).withOutputKeyPrefix(awsKeyPrefix(orig))
                .withOutputs(outputs);

        // TODO: try creating the job 3 times (with a delay between attempts)
        final CreateJobResult result = this.transcoder.createJob(request);

        // return the id of the enqueued job
        return result.getJob().getId();
    }

    boolean checkEncodingJob(final Video orig, final String jobId) {
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

        // fetch a couple common values used for processing
        final String status = etJob.getStatus();
        final String keyPrefix = etJob.getOutputKeyPrefix();

        // get a list of all output files
        final Set<AwsFile> files = new HashSet<>();
        if ("Complete".equals(status) || "Canceled".equals(status) || "Error".equals(status)) {
            // fetch all output files that will be needed for processing
            try {
                ObjectListing result = this.s3.listObjects(awsS3BucketEncoded, keyPrefix);
                while (true) {
                    for (final S3ObjectSummary obj : result.getObjectSummaries()) {
                        files.add(new AwsFile(obj.getBucketName(), obj.getKey()));
                    }

                    if (!result.isTruncated()) {
                        break;
                    }

                    result = this.s3.listNextBatchOfObjects(result);
                }
            } catch (final Exception e) {
                LOG.debug("error retrieving output files from S3", e);
                return false;
            }
        }

        // parse all output HLS playlists
        final Map<Type, Playlist> playlists = new HashMap<>();
        if ("Complete".equals(status)) {
            // parse playlist for any HLS output
            for (final JobOutput output : etJob.getOutputs()) {
                final Type type = Type.fromPreset(output.getPresetId());
                if (type.isHls()) {
                    try (final S3Object in = this.s3.getObject(this.awsS3BucketEncoded, keyPrefix + output.getKey()
                            + ".m3u8");) {
                        try (final PlaylistParser parser = new PlaylistParser(in.getObjectContent())) {
                            final Playlist playlist = parser.parse();
                            playlists.put(type, playlist);
                        }
                    } catch (final Exception e) {
                        LOG.debug("error parsing HLS playlist from S3", e);
                        return false;
                    }
                }
            }
        }

        // update Video record with current state of Job
        try {
            return this.txService.inTransaction(new Callable<Boolean>() {
                @Override
                public Boolean call() {
                    // find the job in the DB
                    final Video video = manager.getManaged(orig);
                    final AwsJob job = video.getJob(jobId);
                    if (job != null) {
                        // update last checked time
                        job.updateLastChecked();

                        // process based on status
                        if (status != null) {
                            switch (status) {
                            case "Complete":
                                // only record complete jobs that are not stale
                                if (!job.isStale()) {
                                    // capture a thumbnail we might use
                                    AwsFile thumbnail = null;

                                    for (final JobOutput jobOutput : etJob.getOutputs()) {
                                        final Type type = Type.fromPreset(jobOutput.getPresetId());
                                        final String outputKey = keyPrefix + jobOutput.getKey();

                                        // remove old output (protecting files that are in the new output)
                                        final AwsOutput oldOutput = video.getOutput(type);
                                        if (oldOutput != null) {
                                            final Set<AwsFile> protectedFiles = new HashSet<>();
                                            protectedFiles.add(video.getThumbnail());
                                            protectedFiles.addAll(files);

                                            manager.delete(oldOutput, protectedFiles);
                                            em.flush();
                                        }

                                        // generate new AwsOutput object
                                        final AwsOutput output = new AwsOutput(video, type);

                                        // set output playlist/video
                                        output.setFile(new AwsFile(awsS3BucketEncoded, outputKey
                                                + (output.isHls() ? ".m3u8" : "")));
                                        files.remove(output.getFile());

                                        // TODO: set output resolution

                                        // attach any thumbnails for this output
                                        String thumbPattern = jobOutput.getThumbnailPattern();
                                        if (StringUtils.isNotBlank(thumbPattern)) {
                                            final int endIndex = thumbPattern.indexOf("{");
                                            if (endIndex > 0) {
                                                thumbPattern = keyPrefix + thumbPattern.substring(0, endIndex);
                                                for (final AwsFile file : files) {
                                                    if (file != null && file.exists()
                                                            && file.getKey().startsWith(thumbPattern)) {
                                                        output.addThumbnail(file);

                                                        if (thumbnail == null) {
                                                            thumbnail = file;
                                                        }
                                                    }
                                                }
                                            }

                                            // remove any thumbnails we are storing in this output
                                            files.removeAll(output.getThumbnails());
                                        }

                                        // attach any HLS segments
                                        if (output.isHls()) {
                                            // find all the segments that exist
                                            final Set<AwsFile> segments = new HashSet<>();
                                            for (final AwsFile file : files) {
                                                if (file.getKey().startsWith(outputKey)) {
                                                    segments.add(file);
                                                }
                                            }

                                            // process playlist
                                            final Playlist playlist = playlists.get(type);
                                            for (final Element element : playlist) {
                                                if (element instanceof Media) {
                                                    // create the segment
                                                    final HlsSegment segment = new HlsSegment();
                                                    segment.setDuration(((Media) element).getDuration());

                                                    // find the file for this segment
                                                    for (final AwsFile file : segments) {
                                                        if (file.getKey().endsWith(element.getUri())) {
                                                            segment.setFile(file);
                                                            files.remove(file);
                                                            segments.remove(file);
                                                            break;
                                                        }
                                                    }

                                                    // throw an exception if we couldn't find the requested segment
                                                    if (segment.getFile() == null) {
                                                        throw new RuntimeException("file for HLS segment not found: "
                                                                + element.getUri());
                                                    }

                                                    // add the segment to the output
                                                    output.addSegment(segment);
                                                }
                                            }
                                        }

                                        // save the new output
                                        em.persist(output);
                                        em.flush();
                                        em.refresh(video);
                                    }

                                    // should we replace the thumbnail?
                                    final AwsFile oldThumb;
                                    if (thumbnail != null
                                            && (video.isStaleThumbnail() || (oldThumb = video.getThumbnail()) == null || !oldThumb
                                                    .exists())) {
                                        replaceThumbnail(video, thumbnail);
                                    }
                                }
                            case "Canceled":
                            case "Error":
                                // delete all potential output files if the job is stale or was canceled
                                if (job.isStale() || "Canceled".equals(status)) {
                                    // prevent deletion of any files currently being used
                                    files.remove(video.getThumbnail());
                                    for (final AwsOutput output : video.getOutputs()) {
                                        files.remove(output.getFile());
                                        files.removeAll(output.getFiles());
                                        files.removeAll(output.getThumbnails());
                                    }

                                    // delete any remaining files
                                    manager.deleteFiles(files);
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

    public URL getSignedUrl(final AwsFile file) {
        if (file != null && file.exists()) {
            // calculate next expiration time
            long expiration = System.currentTimeMillis();
            expiration += ((DEFAULT_PRESIGNED_URL_MIN_AGE / 2) - (expiration % (DEFAULT_PRESIGNED_URL_MIN_AGE / 2)));
            expiration += DEFAULT_PRESIGNED_URL_MIN_AGE;

            try {
                return this.s3.generatePresignedUrl(file.getBucket(), file.getKey(), new Date(expiration));
            } catch (final Exception e) {
                // log error, but suppress it
                LOG.error("error generating presigned url", e);
            }
        }

        return null;
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

        // generate the base filename for this output
        String name = extractName(master.getKey());
        final int i = name.lastIndexOf(".");
        if (i >= 0 && i < name.length() - 1) {
            name = name.substring(0, i);
        }

        // find the extension for the output
        switch (type) {
        case MP4_720P:
            // we only output thumbnails for the highest quality MP4 stream
            output.setThumbnailPattern(type + "/thumbnails/thumbnail-{count}");
        case MP4_480P_16_9:
            // all MP4 outputs should have an .mp4 extension
            name = name + ".mp4";
            break;
        case HLS_400K:
        case HLS_1M:
        case HLS_2M:
            // create HLS segments that are 10 seconds long
            output.withSegmentDuration("10");
            break;
        default:
            return null;
        }

        // set output key
        output.setKey(type + "/output/" + name);

        // return the generated output
        return output;
    }

    private String awsKeyPrefix(final Video video) {
        return (this.awsS3KeyPrefix != null ? this.awsS3KeyPrefix : "") + Long.valueOf(video.getId()).toString() + "/"
                + Long.valueOf(video.getVersion()).toString() + "/";
    }

    @Transactional(propagation = Propagation.MANDATORY)
    private void replaceThumbnail(final Video video, final AwsFile thumb) {
        // remove old thumbnail first
        final AwsFile old = video.getThumbnail();
        if (old != null && old.exists()) {
            // gather all available thumbnails for this file
            final Set<AwsFile> thumbs = new HashSet<>();
            for (final AwsOutput output : video.getOutputs()) {
                thumbs.addAll(output.getThumbnails());
            }

            // make sure the thumbnail isn't referenced elsewhere
            if (!thumbs.contains(old)) {
                manager.delete(old);
            }
        }

        // set new thumbnail and clear stale flag
        video.setThumbnail(thumb);
        video.setStaleThumbnail(false);
    }
}
