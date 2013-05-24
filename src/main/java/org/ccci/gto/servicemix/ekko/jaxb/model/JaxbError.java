package org.ccci.gto.servicemix.ekko.jaxb.model;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import org.ccci.gto.servicemix.ekko.EkkoException;
import org.ccci.gto.servicemix.ekko.MultipleManifestExceptions;

@XmlRootElement(name = "error")
public class JaxbError {
    @XmlAttribute(name = "code")
    private Long code;

    @XmlAttribute(name = "message")
    private String message;

    public JaxbError() {
    }

    public JaxbError(final EkkoException exception) {
        if (exception != null) {
            this.code = exception.getCode();
            this.message = exception.getMessage();
        }
    }

    public static JaxbError fromException(final EkkoException e) {
        if (e instanceof MultipleManifestExceptions) {
            return new JaxbErrors((MultipleManifestExceptions) e);
        } else {
            return new JaxbError(e);
        }
    }
}
