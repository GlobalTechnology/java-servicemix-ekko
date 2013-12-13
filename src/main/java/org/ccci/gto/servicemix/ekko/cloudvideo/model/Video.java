package org.ccci.gto.servicemix.ekko.cloudvideo.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.persistence.AttributeOverride;
import javax.persistence.AttributeOverrides;
import javax.persistence.CascadeType;
import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToMany;
import javax.persistence.UniqueConstraint;
import javax.persistence.Version;

import org.ccci.gto.servicemix.ekko.cloudvideo.model.AwsOutput.Type;

@Entity
@NamedQueries({
        @NamedQuery(name = "Video.findByState", query = "SELECT DISTINCT v FROM Video v WHERE v.state = :state"),
        @NamedQuery(name = "Video.findByJobId", query = "SELECT DISTINCT v FROM Video v JOIN v.jobs j WHERE j.id = :jobId"),
        @NamedQuery(name = "Video.findByOldJobs", query = "SELECT DISTINCT v FROM Video v JOIN v.jobs j WHERE j.lastChecked < :date"), })
public class Video {
    public enum State {
        NEW, NEW_MASTER, ENCODING, CHECK, ENCODED
    }

    @Id
    @Column(updatable = false)
    private long id = 0;

    @Version
    @Column(nullable = false)
    private int version = 0;

    @Column(nullable = false)
    private boolean locked = false;

    @Column(nullable = false)
    private long lockTimestamp = 0L;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private State state = State.NEW;

    @Column(nullable = false)
    private String title = "";

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "Video_jobs", joinColumns = @JoinColumn(name = "videoId", referencedColumnName = "id"), uniqueConstraints = @UniqueConstraint(columnNames = {
            "videoId", "jobId" }))
    private List<AwsJob> jobs = new ArrayList<>();

    @Embedded
    @AttributeOverrides({ @AttributeOverride(name = "bucket", column = @Column(name = "master_awsBucket")),
            @AttributeOverride(name = "key", column = @Column(name = "master_awsKey")), })
    private AwsFile master = null;

    @Embedded
    @AttributeOverrides({ @AttributeOverride(name = "bucket", column = @Column(name = "thumbnail_awsBucket")),
            @AttributeOverride(name = "key", column = @Column(name = "thumbnail_awsKey")), })
    private AwsFile thumbnail = null;

    @Column(nullable = false)
    private boolean staleThumbnail = false;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "video", cascade = CascadeType.REMOVE)
    private List<AwsOutput> outputs;

    public final long getId() {
        return this.id;
    }

    public final void setId(final long id) {
        this.id = id;
    }

    public final long getVersion() {
        return version;
    }

    public void setState(final State state) {
        this.state = state;
    }

    public State getState() {
        return this.state;
    }

    public boolean isInState(final State state) {
        return this.state == state;
    }

    public final String getTitle() {
        return this.title;
    }

    public final void setTitle(final String title) {
        this.title = title != null ? title : "";
    }

    public void addJob(final String jobId) {
        this.addJob(new AwsJob(jobId));
    }

    public void addJob(final AwsJob job) {
        if (this.jobs == null) {
            this.jobs = new ArrayList<>();
        }
        this.jobs.add(job);
    }

    public final List<AwsJob> getJobs() {
        return this.jobs != null ? Collections.unmodifiableList(this.jobs) : Collections.<AwsJob> emptyList();
    }

    public final AwsJob getJob(final String jobId) {
        if (this.jobs != null && jobId != null) {
            for (final AwsJob job : this.jobs) {
                if (jobId.equals(job.getId())) {
                    return job;
                }
            }
        }

        return null;
    }

    public final void removeJob(final AwsJob job) {
        if (job != null) {
            this.removeJob(job.getId());
        }
    }

    public final void removeJob(final String job) {
        if (this.jobs != null && job != null) {
            for (int i = 0; i < this.jobs.size(); i++) {
                if (job.equals(this.jobs.get(i).getId())) {
                    this.jobs.remove(i);
                    break;
                }
            }
        }
    }

    public AwsFile getMaster() {
        return this.master;
    }

    public void setMaster(final AwsFile master) {
        this.master = master;
    }

    public final boolean isStaleThumbnail() {
        return this.staleThumbnail;
    }

    public final void setStaleThumbnail(final boolean staleThumbnail) {
        this.staleThumbnail = staleThumbnail;
    }

    public AwsFile getThumbnail() {
        return this.thumbnail;
    }

    public void setThumbnail(final AwsFile thumbnail) {
        this.thumbnail = thumbnail;
    }

    public final AwsOutput getOutput(final Type type) {
        if (this.outputs != null) {
            for (final AwsOutput output : this.outputs) {
                if (output.getType() == type) {
                    return output;
                }
            }
        }

        return null;
    }

    public final List<AwsOutput> getOutputs() {
        if (this.outputs == null) {
            this.outputs = new ArrayList<>();
        }

        return this.outputs;
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
