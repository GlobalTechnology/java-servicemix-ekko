package org.ccci.gto.servicemix.ekko.model;

import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.Lob;

@Entity
public class Course {
    @Id
    @Column(updatable = false)
    private Long id;

    private Long version = 0L;

    private String title;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    private String manifest;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    private String newManifest;

    public Long getId() {
        return this.id;
    }

    public Long getVersion() {
        return this.version;
    }

    public String getTitle() {
        return this.title;
    }

    public String getManifest() {
        return this.manifest;
    }

    public String getNewManifest() {
        return this.newManifest;
    }

    public void setId(final Long id) {
        this.id = id;
    }

    public void incVersion() {
        this.version++;
    }

    public void setTitle(final String title) {
        this.title = title;
    }

    public void setManifest(final String manifest) {
        this.manifest = manifest;
    }

    public void setNewManifest(final String newManifest) {
        this.newManifest = newManifest;
    }
}
