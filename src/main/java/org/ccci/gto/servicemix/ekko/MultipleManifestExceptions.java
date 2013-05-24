package org.ccci.gto.servicemix.ekko;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

public class MultipleManifestExceptions extends ManifestException {
    private static final long serialVersionUID = 5626953930683157624L;

    private final Collection<ManifestException> exceptions;

    public MultipleManifestExceptions(final Collection<? extends ManifestException> exceptions) {
        super();
        if (exceptions == null) {
            this.exceptions = Collections.emptySet();
        } else {
            this.exceptions = Collections.unmodifiableCollection(new ArrayList<ManifestException>(exceptions));
        }
    }

    public Collection<ManifestException> getExceptions() {
        return this.exceptions;
    }
}
