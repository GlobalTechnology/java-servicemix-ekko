package org.ccci.gto.servicemix.ekko.cloudvideo.model;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

@Embeddable
public class AwsJob {
    @Column(name = "jobId", nullable = false, updatable = false, length = 30, unique = true)
    private String id;
    @Column(nullable = false)
    private boolean stale = false;

    @Column(nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date lastChecked = new Date();

    public AwsJob() {
    }

    public AwsJob(final String id) {
        this.id = id;
    }

    public final String getId() {
        return this.id;
    }

    public final void updateLastChecked() {
        this.lastChecked = new Date();
    }

    public final Date getLastChecked() {
        return this.lastChecked;
    }

    public final boolean isStale() {
        return this.stale;
    }

    public final void setStale(final boolean stale) {
        this.stale = stale;
    }
}
