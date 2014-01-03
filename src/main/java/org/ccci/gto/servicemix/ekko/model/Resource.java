package org.ccci.gto.servicemix.ekko.model;

import javax.persistence.Column;
import javax.persistence.MappedSuperclass;

@MappedSuperclass
public abstract class Resource {
    @Column(nullable = false)
    private boolean published = false;
    @Column(nullable = false)
    private boolean metaResource = false;

    public abstract Course getCourse();

    public boolean isPublished() {
        return this.published;
    }

    public boolean isMetaResource() {
        return this.metaResource;
    }

    public void setPublished(final boolean published) {
        this.published = published;
    }

    public void setMetaResource(final boolean metaResource) {
        this.metaResource = metaResource;
    }

    public boolean isVisibleTo(final String guid) {
        final Course course = this.getCourse();
        return course.isAdmin(guid) || (this.isPublished() && course.isContentVisibleTo(guid))
                || (this.isMetaResource() && course.isVisibleTo(guid));
    }
}
