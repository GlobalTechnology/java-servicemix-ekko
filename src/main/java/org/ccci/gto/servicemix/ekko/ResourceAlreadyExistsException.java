package org.ccci.gto.servicemix.ekko;

import org.ccci.gto.servicemix.ekko.model.FileResource;

public final class ResourceAlreadyExistsException extends ResourceException {
    private static final long serialVersionUID = -7818732603419781759L;

    private final FileResource.PrimaryKey resourceKey;

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

    public ResourceAlreadyExistsException(final String message, final FileResource.PrimaryKey resourceKey) {
        super(message);
        this.resourceKey = resourceKey;
    }

    public ResourceAlreadyExistsException(final Throwable cause, final FileResource.PrimaryKey resourceKey) {
        super(cause);
        this.resourceKey = resourceKey;
    }

    public FileResource.PrimaryKey getResourceKey() {
        return this.resourceKey;
    }
}
