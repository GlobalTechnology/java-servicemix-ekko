package org.ccci.gto.servicemix.ekko;

public class ResourceNotFoundException extends ResourceException {
    private static final long serialVersionUID = -290012987834571241L;

    public ResourceNotFoundException() {
    }

    public ResourceNotFoundException(final String message) {
        super(message);
    }

    public ResourceNotFoundException(final Throwable cause) {
        super(cause);
    }

    public ResourceNotFoundException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
