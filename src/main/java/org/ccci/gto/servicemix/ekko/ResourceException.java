package org.ccci.gto.servicemix.ekko;

public class ResourceException extends Exception {
    private static final long serialVersionUID = -3094601553703584626L;

    public ResourceException() {
    }

    public ResourceException(final String message) {
        super(message);
    }

    public ResourceException(final Throwable cause) {
        super(cause);
    }

    public ResourceException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
