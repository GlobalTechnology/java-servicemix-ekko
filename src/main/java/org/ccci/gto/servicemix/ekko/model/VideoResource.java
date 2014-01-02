package org.ccci.gto.servicemix.ekko.model;

import java.io.Serializable;

import javax.persistence.Embeddable;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.MapsId;
import javax.persistence.Table;

import org.apache.openjpa.persistence.jdbc.ForeignKey;
import org.apache.openjpa.persistence.jdbc.ForeignKeyAction;
import org.ccci.gto.servicemix.ekko.cloudvideo.model.Video;

@Entity
@Table(name = "Course_videos")
public class VideoResource extends AbstractResource {
    @EmbeddedId
    private PrimaryKey key;

    @MapsId("courseId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "courseId", referencedColumnName = "id")
    @ForeignKey(updateAction = ForeignKeyAction.CASCADE, deleteAction = ForeignKeyAction.CASCADE)
    private Course course;

    @MapsId("videoId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "videoId", referencedColumnName = "id")
    @ForeignKey(updateAction = ForeignKeyAction.CASCADE, deleteAction = ForeignKeyAction.RESTRICT)
    private Video video;

    public VideoResource() {
    }

    public VideoResource(final Course course, final long videoId) {
        this(new PrimaryKey(course, videoId));
    }

    public VideoResource(final PrimaryKey key) {
        this.key = key;
    }

    public final long getCourseId() {
        return this.key.courseId;
    }

    public final Course getCourse() {
        return this.course;
    }

    public final long getVideoId() {
        return this.key.videoId;
    }

    public final Video getVideo() {
        return this.video;
    }

    @Embeddable
    public static class PrimaryKey implements Serializable {
        private static final long serialVersionUID = 8165589067386075372L;

        private long courseId;

        private long videoId;

        public PrimaryKey() {
        }

        public PrimaryKey(final Course course, final Video video) {
            this(course, video.getId());
        }

        public PrimaryKey(final Course course, final long videoId) {
            this(course.getId(), videoId);
        }

        protected PrimaryKey(final long courseId, final long videoId) {
            this.courseId = courseId;
            this.videoId = videoId;
        }

        public final long getCourseId() {
            return this.courseId;
        }

        public final long getVideoId() {
            return this.videoId;
        }

        @Override
        public int hashCode() {
            int hash = 0;
            hash = (hash * 31) + Long.valueOf(this.courseId).hashCode();
            hash = (hash * 31) + Long.valueOf(this.videoId).hashCode();
            return hash;
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == null || !(obj instanceof PrimaryKey)) {
                return false;
            }
            final PrimaryKey key2 = (PrimaryKey) obj;
            return (this.courseId == key2.courseId) && (this.videoId == key2.videoId);
        }
    }
}
