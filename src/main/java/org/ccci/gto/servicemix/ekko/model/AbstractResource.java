package org.ccci.gto.servicemix.ekko.model;

import javax.persistence.Column;
import javax.persistence.MappedSuperclass;

@MappedSuperclass
public abstract class AbstractResource {
    @Column(nullable = false)
    private boolean published = false;
    @Column(nullable = false)
    private boolean metaResource = false;

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
}
