package org.ccci.gto.servicemix.ekko;

public class MissingVideoResourceManifestException extends MissingResourceManifestException {
    private static final long serialVersionUID = 7470740779506681584L;

    private final Long videoId;

    public MissingVideoResourceManifestException() {
        this(null);
    }

    public MissingVideoResourceManifestException(final Long videoId) {
        super("Video resource missing on Ekko Cloud");
        this.videoId = videoId;
    }

    public Long getVideoId() {
        return this.videoId;
    }
}
