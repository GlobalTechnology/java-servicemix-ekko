package org.ccci.gto.servicemix.ekko.jaxb.model;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;

import org.ccci.gto.servicemix.ekko.EkkoException;
import org.ccci.gto.servicemix.ekko.MultipleManifestExceptions;

@XmlRootElement(name = "errors")
public class JaxbErrors extends JaxbError {
    @XmlElementRef
    private List<JaxbError> errors = new ArrayList<JaxbError>();

    public JaxbErrors() {
        super();
    }

    public JaxbErrors(final MultipleManifestExceptions exception) {
        super(exception);
        if (exception != null) {
            for (final EkkoException e : exception.getExceptions()) {
                this.errors.add(JaxbError.fromException(e));
            }
        }
    }

    public void addError(final EkkoException e) {
        this.errors.add(JaxbError.fromException(e));
    }
}
