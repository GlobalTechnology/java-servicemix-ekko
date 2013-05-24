package org.ccci.gto.servicemix.ekko;

public abstract class EkkoException extends Exception {
    private static final long serialVersionUID = -7390566937877656213L;

    private final Long code;

    public EkkoException() {
        this.code = null;
    }

    public EkkoException(final String message) {
        super(message);
        this.code = null;
    }

    public EkkoException(final Throwable cause) {
        super(cause);
        this.code = null;
    }

    public EkkoException(final String message, final Throwable cause) {
        super(message, cause);
        this.code = null;
    }

    public Long getCode() {
        return this.code;
    }
}
