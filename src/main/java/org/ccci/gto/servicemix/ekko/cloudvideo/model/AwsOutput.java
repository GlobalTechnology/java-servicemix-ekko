package org.ccci.gto.servicemix.ekko.cloudvideo.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
import javax.persistence.OrderColumn;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

import org.apache.openjpa.persistence.jdbc.ForeignKey;
import org.apache.openjpa.persistence.jdbc.ForeignKeyAction;

@Entity
@Table(name = "EncodedVideos")
public class AwsOutput {
    private static final String PRESET_HLS_400K = "1351620000001-200050";
    private static final String PRESET_HLS_1M = "1351620000001-200030";
    private static final String PRESET_HLS_2M = "1351620000001-200010";
    private static final String PRESET_MP4_480P_16_9 = "1351620000001-000020";
    private static final String PRESET_MP4_720P = "1351620000001-000010";

    public enum Type {
        UNKNOWN(null), MP4(null), MP4_480P_16_9(PRESET_MP4_480P_16_9), MP4_720P(PRESET_MP4_720P), HLS(null), HLS_400K(
                PRESET_HLS_400K), HLS_1M(PRESET_HLS_1M), HLS_2M(PRESET_HLS_2M);

        public final String preset;

        private Type(final String preset) {
            this.preset = preset;
        }

        public static Type fromPreset(final String preset) {
            if (preset != null) {
                switch (preset) {
                case PRESET_HLS_400K:
                    return HLS_400K;
                case PRESET_HLS_1M:
                    return HLS_1M;
                case PRESET_HLS_2M:
                    return HLS_2M;
                case PRESET_MP4_480P_16_9:
                    return MP4_480P_16_9;
                case PRESET_MP4_720P:
                    return MP4_720P;
                }
            }

            return UNKNOWN;
        }

        public boolean isHls() {
            switch (this) {
            case HLS:
            case HLS_400K:
            case HLS_1M:
            case HLS_2M:
                return true;
            default:
                return false;
            }
        }

        /* methods that return required HLS meta-data about this type */

        /**
         * @see <a
         *      href="https://developer.apple.com/library/ios/documentation/networkinginternet/conceptual/streamingmediaguide/FrequentlyAskedQuestions/FrequentlyAskedQuestions.html">Apple
         *      HLS FAQ</a>
         * @return
         */
        public String codecs() {
            switch (this) {
            case HLS_400K:
                return "avc1.42001e,mp4a.40.2";
            case HLS_1M:
            case HLS_2M:
                return "avc1.4d001f,mp4a.40.2";
            default:
                return null;
            }
        }

        public int bandwidth() {
            switch (this) {
            case HLS_400K:
                return 463000;
            case HLS_1M:
                return 1108000;
            case HLS_2M:
                return 2193000;
            default:
                return 0;
            }
        }
    }

    public static final Set<Type> REQUIRED_TYPES = Collections.unmodifiableSet(EnumSet.of(Type.HLS_400K, Type.HLS_1M,
            Type.HLS_2M, Type.MP4_720P));

    @EmbeddedId
    private PrimaryKey key;

    @MapsId("videoId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "videoId", referencedColumnName = "id", nullable = false, updatable = false)
    @ForeignKey(updateAction = ForeignKeyAction.CASCADE, deleteAction = ForeignKeyAction.CASCADE)
    private Video video;

    @Column(nullable = false)
    private boolean stale = false;

    private Integer width = null;
    private Integer height = null;

    @Embedded
    private AwsFile file;

    @ElementCollection(fetch = FetchType.LAZY)
    @OrderColumn(name = "segment")
    @CollectionTable(name = "EncodedVideos_segments", joinColumns = {
            @JoinColumn(name = "videoId", referencedColumnName = "videoId"),
            @JoinColumn(name = "type", referencedColumnName = "type") }, uniqueConstraints = {
            @UniqueConstraint(name = "segment", columnNames = { "videoId", "type", "segment" }),
            @UniqueConstraint(name = "awsFile", columnNames = { "videoId", "type", "awsBucket", "awsKey" }) })
    private List<HlsSegment> segments = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "EncodedVideos_thumbnails", joinColumns = {
            @JoinColumn(name = "videoId", referencedColumnName = "videoId"),
            @JoinColumn(name = "type", referencedColumnName = "type") }, uniqueConstraints = @UniqueConstraint(columnNames = {
            "videoId", "type", "awsBucket", "awsKey" }))
    private Set<AwsFile> thumbnails;

    public AwsOutput() {
        this(null);
    }

    public AwsOutput(final Video video, final Type type) {
        this(new PrimaryKey(video, type));
    }

    public AwsOutput(final long videoId, final Type type) {
        this(new PrimaryKey(videoId, type));
    }

    public AwsOutput(final PrimaryKey key) {
        this.key = key != null ? key : new PrimaryKey();
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

    public final List<HlsSegment> getSegments() {
        return this.segments != null ? Collections.unmodifiableList(this.segments) : Collections
                .<HlsSegment> emptyList();
    }

    public final void addSegment(final HlsSegment segment) {
        if (segment != null) {
            if (this.segments == null) {
                this.segments = new ArrayList<>();
            }

            this.segments.add(segment);
        }
    }

    public final List<AwsFile> getFiles() {
        if (this.segments != null && this.segments.size() > 0) {
            final List<AwsFile> files = new ArrayList<>();

            for (final HlsSegment segment : this.segments) {
                files.add(segment.getFile());
            }

            return Collections.unmodifiableList(files);
        }

        return Collections.emptyList();
    }

    public void addThumbnail(final AwsFile file) {
        if (this.thumbnails == null) {
            this.thumbnails = new HashSet<>();
        }

        this.thumbnails.add(file);
    }

    public final Set<AwsFile> getThumbnails() {
        return this.thumbnails != null ? Collections.unmodifiableSet(this.thumbnails) : Collections
                .<AwsFile> emptySet();
    }

    public final void removeThumbnail(final AwsFile file) {
        if (this.thumbnails != null) {
            this.thumbnails.remove(file);
        }
    }

    public final Integer getWidth() {
        return this.width;
    }

    public final Integer getHeight() {
        return this.height;
    }

    public final void setWidth(final Integer width) {
        this.width = width;
    }

    public final void setHeight(final Integer height) {
        this.height = height;
    }

    public boolean isDownloadable() {
        switch (this.key.type) {
        case MP4_720P:
        case MP4_480P_16_9:
            return true;
        default:
            return false;
        }
    }

    public boolean isHls() {
        return this.key.type != null ? this.key.type.isHls() : false;
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
            int hash = 0;
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
