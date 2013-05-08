package org.ccci.gto.servicemix.ekko;

import org.ccci.gto.servicemix.ekko.model.ResourcePrimaryKey;

public final class ResourceAlreadyExistsException extends ResourceException {
    private static final long serialVersionUID = -3042183162575703771L;

    private final ResourcePrimaryKey resourceKey;

    public ResourceAlreadyExistsException() {
        this.resourceKey = null;
    }

    public ResourceAlreadyExistsException(final String message) {
        super(message);
        this.resourceKey = null;
    }

    public ResourceAlreadyExistsException(final Throwable cause) {
        super(cause);
        this.resourceKey = null;
    }

    public ResourceAlreadyExistsException(final String message, final Throwable cause) {
        super(message, cause);
        this.resourceKey = null;
    }

    public ResourceAlreadyExistsException(final String message, final ResourcePrimaryKey resourceKey) {
        super(message);
        this.resourceKey = resourceKey;
    }

    public ResourceAlreadyExistsException(final Throwable cause, final ResourcePrimaryKey resourceKey) {
        super(cause);
        this.resourceKey = resourceKey;
    }

    public ResourcePrimaryKey getResourceKey() {
        return this.resourceKey;
    }
}
