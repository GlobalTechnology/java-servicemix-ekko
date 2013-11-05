package org.ccci.gto.servicemix.ekko.model;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Embeddable;

@Embeddable
public class ResourcePrimaryKey implements Serializable {
    private static final long serialVersionUID = -91895163112177105L;

    private long courseId;
    @Column(length = 40)
    private String sha1 = null;

    public ResourcePrimaryKey() {
    }

    public ResourcePrimaryKey(final Course course, final String sha1) {
        this(course.getId(), sha1);
    }

    protected ResourcePrimaryKey(final long courseId, final String sha1) {
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
        int hash = super.hashCode();
        hash = (hash * 31) + Long.valueOf(this.courseId).hashCode();
        hash = (hash * 31) + this.sha1.hashCode();
        return hash;
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == null || !(obj instanceof ResourcePrimaryKey)) {
            return false;
        }
        final ResourcePrimaryKey key2 = (ResourcePrimaryKey) obj;
        return (this.courseId == key2.courseId) && (this.sha1.equals(key2.sha1));
    }
}
