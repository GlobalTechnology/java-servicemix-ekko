package org.ccci.gto.servicemix.ekko.cloudvideo;

import static org.ccci.gto.servicemix.ekko.cloudvideo.model.Video.STATE_ENCODED;
import static org.ccci.gto.servicemix.ekko.cloudvideo.model.Video.STATE_ENCODING;
import static org.ccci.gto.servicemix.ekko.cloudvideo.model.Video.STATE_NEW;
import static org.ccci.gto.servicemix.ekko.cloudvideo.model.Video.STATE_NEW_MASTER;

import org.ccci.gto.persistence.tx.TransactionService;
import org.ccci.gto.servicemix.ekko.cloudvideo.model.AwsFile;
import org.ccci.gto.servicemix.ekko.cloudvideo.model.AwsFileToDelete;
import org.ccci.gto.servicemix.ekko.cloudvideo.model.AwsFileToUpload;
import org.ccci.gto.servicemix.ekko.cloudvideo.model.Video;
import org.ccci.gto.servicemix.ekko.cloudvideo.util.AWSUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.services.elastictranscoder.AmazonElasticTranscoder;
import com.amazonaws.services.elastictranscoder.AmazonElasticTranscoderClient;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.CopyObjectRequest;
import com.amazonaws.services.s3.model.CopyObjectResult;

public class AwsVideoManagerImpl extends AbstractVideoManager implements AwsVideoManager, InitializingBean {
    private static final Logger LOG = LoggerFactory.getLogger(AwsVideoManagerImpl.class);

    @Autowired
    private TransactionService txService;

    private AWSCredentials credentials = null;
    private AmazonS3 s3 = null;
    private AmazonElasticTranscoder transcoder = null;

    public final void setCredentials(final AWSCredentials credentials) {
        this.credentials = credentials;
    }

    public final void setS3(final AmazonS3 s3) {
        this.s3 = s3;
    }

    public final void setTranscoder(final AmazonElasticTranscoder transcoder) {
        this.transcoder = transcoder;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (this.credentials != null) {
            if (this.s3 == null) {
                this.s3 = new AmazonS3Client(this.credentials);
            }
            if (this.transcoder == null) {
                this.transcoder = new AmazonElasticTranscoderClient(this.credentials);
            }
        }
    }

    @Override
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
                    final AwsFileToUpload upload = new AwsFileToUpload(refresh(video), file);
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

    @Override
    public boolean updateMaster(final Video orig, final AwsFile source) {
        // short-circuit if we don't have a video object or source video
        if (orig == null || source == null || !source.exists()) {
            return false;
        }

        // move new master video
        final CopyObjectRequest request = new CopyObjectRequest(source.getBucket(), source.getKey(),
                source.getVersion(), "ecv-masters", Long.valueOf(orig.getId()).toString() + "/"
                        + AWSUtils.makeUnique(AWSUtils.extractName(source.getKey())));
        final CopyObjectResult result;
        try {
            result = s3.copyObject(request);
        } catch (final Exception e) {
            return false;
        }

        // short-circuit if we don't have a valid result
        if (result == null) {
            return false;
        }

        LOG.debug("result versionId is" + (result.getVersionId() == null ? "null" : "'" + result.getVersionId() + "'"));

        // create AwsFile object for new master video
        final AwsFile master = new AwsFile(request.getDestinationBucketName(), request.getDestinationKey(),
                result.getVersionId());

        // update video object with new master
        try {
            txService.inTransaction(new Runnable() {
                @Override
                public void run() {
                    final Video video = refresh(orig);

                    // delete previous master
                    deleteAwsFile(video.getMaster());

                    // store new master
                    video.setMaster(master);

                    // transition to the new_master state
                    transitionTo(video, STATE_NEW_MASTER);

                    // mark old thumbnail as stale
                    final AwsFile thumb = video.getThumbnail();
                    if (thumb != null) {
                        thumb.setStale(thumb.exists());
                    }
                }
            });
        } catch (final Exception e1) {
            // we delete the orphaned AwsFile that wasn't correctly recorded
            LOG.debug("error recording new master", e1);
            try {
                txService.inTransaction(new Runnable() {
                    @Override
                    public void run() {
                        LOG.debug("trying to clean up orphaned AwsFile");
                        deleteAwsFile(master);
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

    @Transactional(propagation = Propagation.MANDATORY)
    private void deleteAwsFile(final AwsFile file) {
        if (file != null && file.exists()) {
            em.persist(new AwsFileToDelete(new AwsFile(file)));
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    private void transitionTo(final Video video, final int state) {
        switch (state) {
        case STATE_NEW_MASTER:
            switch (video.getState()) {
            case STATE_NEW:
            case STATE_NEW_MASTER:
            case STATE_ENCODING:
            case STATE_ENCODED:
                video.setState(state);
                break;
            default:
                throw new RuntimeException("cannot transition to STATE_HAS_MASTER from current state");
            }
            break;
        default:
            throw new RuntimeException("Invalid state specified for transition");
        }
    }
}
