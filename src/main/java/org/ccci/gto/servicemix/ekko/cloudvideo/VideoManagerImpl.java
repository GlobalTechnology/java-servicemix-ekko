package org.ccci.gto.servicemix.ekko.cloudvideo;

import java.security.SecureRandom;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.persistence.EntityExistsException;
import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import javax.persistence.PersistenceContext;

import org.ccci.gto.servicemix.ekko.cloudvideo.model.AwsFile;
import org.ccci.gto.servicemix.ekko.cloudvideo.model.AwsFileToDelete;
import org.ccci.gto.servicemix.ekko.cloudvideo.model.AwsFileToUpload;
import org.ccci.gto.servicemix.ekko.cloudvideo.model.AwsJob;
import org.ccci.gto.servicemix.ekko.cloudvideo.model.AwsOutput;
import org.ccci.gto.servicemix.ekko.cloudvideo.model.Video;
import org.ccci.gto.servicemix.ekko.cloudvideo.model.Video.VideoQuery;
import org.ccci.gto.servicemix.ekko.model.VideoResource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public class VideoManagerImpl implements VideoManager {
    private static final SecureRandom RAND = new SecureRandom();

    @PersistenceContext
    protected EntityManager em;

    @Override
    @Transactional
    public Video createVideo(final Video video) {
        while (true) {
            try {
                // generate a new id for this course
                long id = 0;
                while (id <= 0) {
                    id = RAND.nextLong();
                }
                video.setId(id);

                // save this course
                this.em.persist(video);
            } catch (final EntityExistsException e) {
                // a course with the specified id exists, try again
                continue;
            }

            // we created the course successfully, break processing
            break;
        }

        return video;
    }

    @Override
    @Transactional(readOnly = true)
    public Video getVideo(final long id) {
        return this.em.find(Video.class, id);
    }

    @Override
    @Transactional(readOnly = true)
    public Video getVideo(final VideoQuery query) {
        final List<Video> videos = this.getVideos(query.clone().limit(1));
        return videos.size() > 0 ? videos.get(0) : null;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Video> getVideos(final VideoQuery query) {
        return query.execute(this.em);
    }

    @Override
    @Transactional
    public Video getManaged(final Video video) {
        return getManaged(video, LockModeType.NONE);
    }

    @Override
    @Transactional
    public Video getManaged(final Video video, final LockModeType lock) {
        if (video == null) {
            return null;
        } else if (this.em.contains(video)) {
            this.em.lock(video, lock);
            return video;
        } else {
            return this.em.find(Video.class, video.getId(), lock);
        }
    }

    @Override
    @Transactional
    public void updateCourses(final Video video, final Collection<Long> toAdd, final Collection<Long> toRemove) {
        final long videoId = video.getId();

        // process all removals
        if (toRemove != null && toRemove.size() > 0) {
            for (final Long courseId : toRemove) {
                final VideoResource resource = this.em.find(VideoResource.class, new VideoResource.PrimaryKey(courseId,
                        videoId));

                // only remove mappings for courses that haven't published this video
                if (resource != null && !resource.isPublished()) {
                    this.em.remove(resource);
                    this.em.flush();
                }
            }
        }

        // process all additions
        if (toAdd != null && toAdd.size() > 0) {
            for (final Long courseId : toAdd) {
                final VideoResource resource = this.em.find(VideoResource.class, new VideoResource.PrimaryKey(courseId,
                        videoId));

                // only attempt adding if we don't currently have a mapping
                if (resource == null) {
                    this.em.persist(new VideoResource(courseId, videoId));
                    this.em.flush();
                }
            }
        }
    }

    @Override
    @Transactional
    public boolean preDelete(final Video orig) {
        final Video video = getManaged(orig, LockModeType.PESSIMISTIC_WRITE);
        if (video == null) {
            return false;
        }

        // make sure there are no Courses actively using this video
        for (final VideoResource resource : video.getVideoResources()) {
            if (resource.isPublished()) {
                return false;
            }
        }

        // revoke all course usage
        for (final VideoResource resource : video.getVideoResources()) {
            this.em.remove(resource);
        }

        // mark course for deletion
        video.setDeleted(true);

        // mark any pending encoding jobs as stale
        for (final AwsJob job : video.getJobs()) {
            job.setStale(true);
        }

        // return success
        return true;
    }

    @Override
    @Transactional
    public boolean delete(final Video orig) {
        final Video video = getManaged(orig, LockModeType.PESSIMISTIC_WRITE);

        // preDelete to ensure linked objects are ready for deletion
        if (!this.preDelete(video)) {
            // short-circuit if preDelete fails
            return false;
        }

        // delete all outputs
        for (final AwsOutput output : video.getOutputs()) {
            this.delete(output, null);
        }

        // short-circuit if we still have active jobs
        if (!video.getJobs().isEmpty()) {
            return false;
        }

        // delete any remaining files
        this.delete(video.getThumbnail());
        this.delete(video.getMaster());

        // flush all changes before removing the actual video
        this.em.flush();

        // delete video
        this.em.remove(video);

        // return success
        return true;
    }

    @Override
    @Transactional
    public void delete(final AwsFile file) {
        if (file != null && file.exists()) {
            this.em.persist(new AwsFileToDelete(file));
        }
    }

    @Override
    @Transactional
    public void deleteFiles(final Collection<AwsFile> files) {
        if (files != null) {
            for (final AwsFile file : files) {
                delete(file);
            }
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void delete(final AwsFileToUpload upload, final Collection<AwsFile> protectedFiles) {
        if (upload.isDeleteSource() && (protectedFiles == null || !protectedFiles.contains(upload.getFile()))) {
            delete(upload.getFile());
        }

        this.em.remove(upload);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void delete(final AwsOutput output, final Collection<AwsFile> protectedFiles) {
        // get all the files that need to be deleted
        final Set<AwsFile> files = new HashSet<>();
        files.add(output.getFile());
        files.addAll(output.getFiles());
        files.addAll(output.getThumbnails());

        // protected the specified files
        if (protectedFiles != null) {
            files.removeAll(protectedFiles);
        }

        // delete all AwsFiles
        deleteFiles(files);

        // delete AwsOutput
        this.em.remove(output);
    }
}
