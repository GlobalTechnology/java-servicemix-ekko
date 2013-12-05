package org.ccci.gto.servicemix.ekko.cloudvideo;

import org.ccci.gto.servicemix.ekko.cloudvideo.model.AwsFile;
import org.ccci.gto.servicemix.ekko.cloudvideo.model.Video;

public interface AwsVideoManager extends VideoManager {
    boolean enqueueUpload(Video video, AwsFile file, boolean deleteAfterUpload);

    /**
     * update the master video. This method requires the state machine lock for
     * this video before processing.
     * 
     * @param video
     * @param file
     * @return
     */
    boolean updateMaster(Video video, AwsFile file);
}
