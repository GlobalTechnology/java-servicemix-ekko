package org.ccci.gto.servicemix.ekko.model;

import java.io.Serializable;

import javax.persistence.Column;
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

@Entity
@Table(name = "Resource")
public class FileResource extends Resource {
    @EmbeddedId
    private PrimaryKey key;

    @MapsId("courseId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "courseId", referencedColumnName = "id")
    @ForeignKey(updateAction = ForeignKeyAction.CASCADE, deleteAction = ForeignKeyAction.RESTRICT)
    private Course course;

    private String mogileFsKey;

    private String mimeType;

    @Column(nullable = true, updatable = false)
    private Long crc32 = null;

    @Column(nullable = false, updatable = false)
    private long size = 0;

    public FileResource() {
        this(new PrimaryKey());
    }

    public FileResource(final Course course, final String sha1) {
        this(new PrimaryKey(course, sha1));
    }

    public FileResource(final Long courseId, final String sha1) {
        this(new PrimaryKey(courseId, sha1));
    }

    public FileResource(final PrimaryKey key) {
        this.key = key;
    }

    public PrimaryKey getKey() {
        return this.key;
    }

    @Override
    public final Course getCourse() {
        return this.course;
    }

    public String getSha1() {
        return this.key.getSha1();
    }

    public String getMogileFsKey() {
        return this.mogileFsKey;
    }

    public String getMimeType() {
        return this.mimeType;
    }

    public long getSize() {
        return this.size;
    }

    public Long getCrc32() {
        return this.crc32;
    }

    public void setMogileFsKey(final String key) {
        this.mogileFsKey = key;
    }

    public void setMimeType(final String mimeType) {
        this.mimeType = mimeType;
    }

    public void setSize(final long size) {
        this.size = size;
    }

    public void setCrc32(final Long crc32) {
        this.crc32 = crc32;
    }

    @Embeddable
    public static class PrimaryKey implements Serializable {
        private static final long serialVersionUID = -4982314934999687938L;

        private long courseId;
        @Column(length = 40)
        private String sha1 = null;

        public PrimaryKey() {
        }

        public PrimaryKey(final Course course, final String sha1) {
            this(course.getId(), sha1);
        }

        protected PrimaryKey(final long courseId, final String sha1) {
            this.courseId = courseId;
            if (sha1 != null) {
                this.sha1 = sha1.toLowerCase();
            }
        }

        public long getCourseId() {
            return this.courseId;
        }

        /**
         * @return the code
         */
        public String getSha1() {
            return this.sha1;
        }

        @Override
        public int hashCode() {
            int hash = 0;
            hash = (hash * 31) + Long.valueOf(this.courseId).hashCode();
            hash = (hash * 31) + this.sha1.hashCode();
            return hash;
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == null || !(obj instanceof PrimaryKey)) {
                return false;
            }
            final PrimaryKey key2 = (PrimaryKey) obj;
            return (this.courseId == key2.courseId) && (this.sha1.equals(key2.sha1));
        }
    }
}
