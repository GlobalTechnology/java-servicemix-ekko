package org.ccci.gto.servicemix.ekko.cloudvideo.model;

import javax.persistence.AttributeOverride;
import javax.persistence.AttributeOverrides;
import javax.persistence.Column;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.Id;

@Entity
public class Video {
    public static final int STATE_NEW = 0;
    public static final int STATE_NEW_MASTER = 1;
    public static final int STATE_ENCODING = 2;
    public static final int STATE_ENCODED = 3;

    @Id
    @Column(updatable = false)
    private long id = 0;

    @Column(nullable = false)
    private int state = STATE_NEW;

    @Embedded
    @AttributeOverrides({ @AttributeOverride(name = "bucket", column = @Column(name = "master_awsBucket")),
            @AttributeOverride(name = "key", column = @Column(name = "master_awsKey")), })
    private AwsFile master = null;

    @Embedded
    @AttributeOverrides({ @AttributeOverride(name = "bucket", column = @Column(name = "thumbnail_awsBucket")),
            @AttributeOverride(name = "key", column = @Column(name = "thumbnail_awsKey")), })
    private AwsFile thumbnail = null;

    @Column(nullable = false)
    private boolean locked = false;

    @Column(nullable = false)
    private long lockTimestamp = 0L;

    public final long getId() {
        return this.id;
    }

    public final void setId(final long id) {
        this.id = id;
    }

    public int getState() {
        return this.state;
    }

    public void setState(final int state) {
        this.state = state;
    }

    public AwsFile getMaster() {
        return this.master;
    }

    public void setMaster(final AwsFile master) {
        this.master = master;
    }

    public AwsFile getThumbnail() {
        return this.thumbnail;
    }

    public void setThumbnail(final AwsFile thumbnail) {
        this.thumbnail = thumbnail;
    }

    public boolean getLock() {
        if (this.locked) {
            return false;
        }

        this.locked = true;
        this.lockTimestamp = System.currentTimeMillis();
        return true;
    }

    public void releaseLock() {
        this.locked = false;
    }
}
