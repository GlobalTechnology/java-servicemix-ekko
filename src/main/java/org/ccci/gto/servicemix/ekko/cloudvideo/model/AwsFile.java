package org.ccci.gto.servicemix.ekko.cloudvideo.model;

import javax.persistence.Column;
import javax.persistence.Embeddable;

@Embeddable
public class AwsFile {
    @Column(name = "awsBucket", length = 255)
    private String bucket;
    @Column(name = "awsKey")
    private String key;
    @Column(name = "awsVersion")
    private String version;
    private boolean stale = false;

    public AwsFile() {
    }

    public AwsFile(final AwsFile file) {
        this.bucket = file.bucket;
        this.key = file.key;
        this.version = file.version;
        this.stale = file.stale;
    }

    public AwsFile(final String bucket, final String key, final String version) {
        this.bucket = bucket;
        this.key = key;
        this.version = version;
    }

    public final String getBucket() {
        return this.bucket;
    }

    public final String getKey() {
        return this.key;
    }

    public final String getVersion() {
        return this.version;
    }

    public final boolean exists() {
        return this.bucket != null && this.key != null;
    }

    public final boolean isStale() {
        return this.stale;
    }

    public final void setStale(final boolean stale) {
        this.stale = stale;
    }
}
