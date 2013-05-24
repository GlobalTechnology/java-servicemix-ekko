package org.ccci.gto.servicemix.ekko;

public class ManifestException extends EkkoException {
    private static final long serialVersionUID = -3800262692866584697L;

    public ManifestException() {
        super();
    }

    public ManifestException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public ManifestException(final String message) {
        super(message);
    }

    public ManifestException(final Throwable cause) {
        super(cause);
    }
}
