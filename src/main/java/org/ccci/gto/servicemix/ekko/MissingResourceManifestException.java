package org.ccci.gto.servicemix.ekko;

public class MissingResourceManifestException extends ManifestException {
    private static final long serialVersionUID = -4253442141212366417L;

    public MissingResourceManifestException() {
    }

    public MissingResourceManifestException(final String message) {
        super(message);
    }
}
