package org.ccci.gto.servicemix.ekko;

public class MissingResourceManifestException extends ManifestException {
    private static final long serialVersionUID = -3867654209024953830L;

    private final String sha1;

    public MissingResourceManifestException() {
        super();
        this.sha1 = null;
    }

    public MissingResourceManifestException(final String sha1) {
        super("File resource missing on Ekko Hub");
        this.sha1 = sha1;
    }

    public String getSha1() {
        return this.sha1;
    }
}
