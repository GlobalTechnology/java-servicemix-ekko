package org.ccci.gto.servicemix.ekko.cloudvideo;

import java.util.List;

import javax.persistence.LockModeType;

import org.ccci.gto.servicemix.ekko.cloudvideo.model.Video;
import org.ccci.gto.servicemix.ekko.cloudvideo.model.Video.VideoQuery;

public interface VideoManager {
    Video createVideo(Video video);

    Video getVideo(long id);

    List<Video> getVideos(VideoQuery query);

    Video refresh(Video video);

    Video refresh(Video video, LockModeType lock);
}
