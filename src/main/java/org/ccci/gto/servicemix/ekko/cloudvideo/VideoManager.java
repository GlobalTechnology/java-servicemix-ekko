package org.ccci.gto.servicemix.ekko.cloudvideo;

import javax.persistence.LockModeType;

import org.ccci.gto.servicemix.ekko.cloudvideo.model.Video;

public interface VideoManager {
    Video createVideo(Video video);

    Video getVideo(long id);

    Video refresh(Video video);

    Video refresh(Video video, LockModeType lock);
}
