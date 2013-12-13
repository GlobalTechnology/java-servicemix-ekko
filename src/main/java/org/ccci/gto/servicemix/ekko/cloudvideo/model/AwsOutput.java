package org.ccci.gto.servicemix.ekko.cloudvideo.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Embeddable;
import javax.persistence.Embedded;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.MapsId;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

import org.apache.openjpa.persistence.jdbc.ForeignKey;
import org.apache.openjpa.persistence.jdbc.ForeignKeyAction;

@Entity
@Table(name = "EncodedVideos")
public class AwsOutput {
    private static final String PRESET_HLS_1M = "1351620000001-200030";
    private static final String PRESET_MP4_480P_16_9 = "1351620000001-000020";
    public enum Type {
        UNKNOWN(null), HLS_1M(PRESET_HLS_1M), MP4_480P_16_9(PRESET_MP4_480P_16_9);

        public final String preset;

        private Type(final String preset) {
            this.preset = preset;
        }

        public static Type fromPreset(final String preset) {
            if (preset != null) {
                switch (preset) {
                case PRESET_HLS_1M:
                    return HLS_1M;
                case PRESET_MP4_480P_16_9:
                    return MP4_480P_16_9;
                }
            }

            return UNKNOWN;
        }
    }

    @EmbeddedId
    private PrimaryKey key;

    @MapsId("videoId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "videoId", referencedColumnName = "id", nullable = false, updatable = false)
    @ForeignKey(updateAction = ForeignKeyAction.CASCADE, deleteAction = ForeignKeyAction.CASCADE)
    private Video video;

    @Column(nullable = false)
    private boolean stale = false;

    @Embedded
    private AwsFile file;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "EncodedVideos_files", joinColumns = {
            @JoinColumn(name = "videoId", referencedColumnName = "videoId"),
            @JoinColumn(name = "type", referencedColumnName = "type") }, uniqueConstraints = @UniqueConstraint(columnNames = {
            "videoId", "type", "awsBucket", "awsKey" }))
    private List<AwsFile> files = new ArrayList<>();

    public AwsOutput() {
        this(new PrimaryKey());
    }

    public AwsOutput(final Video video, final Type type) {
        this(new PrimaryKey(video, type));
    }

    public AwsOutput(final long videoId, final Type type) {
        this(new PrimaryKey(videoId, type));
    }

    public AwsOutput(final PrimaryKey key) {
        this.key = key;
    }

    public final Video getVideo() {
        return this.video;
    }

    public final Type getType() {
        return this.key.type;
    }

    public final boolean isStale() {
        return this.stale;
    }

    public final void setStale(final boolean stale) {
        this.stale = stale;
    }

    public final AwsFile getFile() {
        return this.file;
    }

    public final void setFile(final AwsFile file) {
        this.file = file;
    }

    public final List<AwsFile> getFiles() {
        return this.files != null ? Collections.unmodifiableList(this.files) : Collections.<AwsFile> emptyList();
    }

    public boolean isHls() {
        switch (this.key.type) {
        case HLS_1M:
            return true;
        default:
            return false;
        }
    }

    @Embeddable
    public static class PrimaryKey implements Serializable {
        private static final long serialVersionUID = -9059668593556740889L;

        @Column(nullable = false, updatable = false)
        private long videoId = 0;
        @Column(nullable = false, updatable = false)
        @Enumerated(EnumType.STRING)
        private Type type = Type.UNKNOWN;

        public PrimaryKey() {
        }

        public PrimaryKey(final Video video, final Type type) {
            this(video != null ? video.getId() : 0, type);
        }

        public PrimaryKey(final long videoId, final Type type) {
            this.videoId = videoId;
            this.type = type;
        }

        @Override
        public int hashCode() {
            int hash = super.hashCode();
            hash = (hash * 31) + Long.valueOf(this.videoId).hashCode();
            hash = (hash * 31) + (this.type != null ? this.type.hashCode() : 0);
            return hash;
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == null || !(obj instanceof PrimaryKey)) {
                return false;
            }
            final PrimaryKey key2 = (PrimaryKey) obj;
            return this.videoId == key2.videoId && this.type == key2.type;
        }
    }
}
