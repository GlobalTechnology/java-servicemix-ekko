package org.ccci.gto.servicemix.ekko;

public class MissingFileResourceManifestException extends MissingResourceManifestException {
    private static final long serialVersionUID = -8769951155255021599L;

    private final String sha1;

    public MissingFileResourceManifestException() {
        this(null);
    }

    public MissingFileResourceManifestException(final String sha1) {
        super("File resource missing on Ekko Cloud");
        this.sha1 = sha1;
    }

    public String getSha1() {
        return this.sha1;
    }
}
