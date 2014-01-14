package org.ccci.gto.servicemix.ekko.cloudvideo;

import java.util.Collection;
import java.util.List;

import javax.persistence.LockModeType;

import org.ccci.gto.servicemix.ekko.cloudvideo.model.AwsFile;
import org.ccci.gto.servicemix.ekko.cloudvideo.model.AwsFileToUpload;
import org.ccci.gto.servicemix.ekko.cloudvideo.model.AwsOutput;
import org.ccci.gto.servicemix.ekko.cloudvideo.model.Video;
import org.ccci.gto.servicemix.ekko.cloudvideo.model.Video.VideoQuery;

public interface VideoManager {
    Video createVideo(Video video);

    Video getVideo(long id);

    Video getVideo(VideoQuery query);

    List<Video> getVideos(VideoQuery query);

    Video getManaged(Video video);

    Video getManaged(Video video, LockModeType lock);

    void updateCourses(Video video, Collection<Long> toAdd, Collection<Long> toRemove);

    boolean preDelete(Video video);

    boolean delete(Video video);

    void delete(AwsFileToUpload upload, Collection<AwsFile> protectedFiles);

    void delete(AwsOutput output, Collection<AwsFile> protectedFiles);

    void deleteFiles(Collection<AwsFile> files);

    void delete(AwsFile file);
}
