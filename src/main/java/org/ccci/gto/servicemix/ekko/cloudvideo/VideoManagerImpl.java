package org.ccci.gto.servicemix.ekko.cloudvideo;

import java.security.SecureRandom;
import java.util.Collection;
import java.util.List;

import javax.persistence.EntityExistsException;
import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import javax.persistence.PersistenceContext;

import org.ccci.gto.servicemix.ekko.cloudvideo.model.Video;
import org.ccci.gto.servicemix.ekko.cloudvideo.model.Video.VideoQuery;
import org.ccci.gto.servicemix.ekko.model.VideoResource;
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
    public Video refresh(final Video video) {
        return refresh(video, LockModeType.NONE);
    }

    @Override
    @Transactional
    public Video refresh(final Video video, final LockModeType lock) {
        return video != null ? this.em.find(Video.class, video.getId(), lock) : null;
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
}
