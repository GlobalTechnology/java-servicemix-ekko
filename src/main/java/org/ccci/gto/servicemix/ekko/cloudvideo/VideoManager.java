package org.ccci.gto.servicemix.ekko.cloudvideo;

import java.util.Collection;
import java.util.List;

import javax.persistence.LockModeType;

import org.ccci.gto.servicemix.ekko.cloudvideo.model.Video;
import org.ccci.gto.servicemix.ekko.cloudvideo.model.Video.VideoQuery;

public interface VideoManager {
    Video createVideo(Video video);

    Video getVideo(long id);

    Video getVideo(VideoQuery query);

    List<Video> getVideos(VideoQuery query);

    Video refresh(Video video);

    Video refresh(Video video, LockModeType lock);

    void updateCourses(Video video, Collection<Long> toAdd, Collection<Long> toRemove);
}
