package org.ccci.gto.servicemix.ekko.cloudvideo;

import java.security.SecureRandom;
import java.util.List;

import javax.persistence.EntityExistsException;
import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import javax.persistence.PersistenceContext;

import org.ccci.gto.servicemix.ekko.cloudvideo.model.Video;
import org.ccci.gto.servicemix.ekko.cloudvideo.model.Video.VideoQuery;
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
}
