package org.ccci.gto.servicemix.ekko.model;

import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.MapsId;

import org.apache.openjpa.persistence.jdbc.ForeignKey;
import org.apache.openjpa.persistence.jdbc.ForeignKeyAction;

@Entity
public class Resource {
    @EmbeddedId
    private ResourcePrimaryKey key;

    @MapsId("courseId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "courseId", referencedColumnName = "id")
    @ForeignKey(updateAction = ForeignKeyAction.CASCADE, deleteAction = ForeignKeyAction.CASCADE)
    private Course course;

    private String mogileFsKey;

    private String mimeType;

    @Column(nullable = true, updatable = false)
    private Long crc32 = null;

    @Column(nullable = false, updatable = false)
    private long size = 0;
    @Column(nullable = false)
    private boolean published = false;
    @Column(nullable = false)
    private boolean metaResource = false;

    public Resource() {
        this(new ResourcePrimaryKey());
    }

    public Resource(final Course course, final String sha1) {
        this(new ResourcePrimaryKey(course, sha1));
    }

    public Resource(final Long courseId, final String sha1) {
        this(new ResourcePrimaryKey(courseId, sha1));
    }

    public Resource(final ResourcePrimaryKey key) {
        this.key = key;
    }

    public ResourcePrimaryKey getKey() {
        return this.key;
    }

    public Course getCourse() {
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

    public boolean isPublished() {
        return this.published;
    }

    public boolean isMetaResource() {
        return this.metaResource;
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

    public void setPublished(final boolean published) {
        this.published = published;
    }

    public void setMetaResource(final boolean metaResource) {
        this.metaResource = metaResource;
    }
}
