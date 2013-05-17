package org.ccci.gto.servicemix.ekko.jaxb.model;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "user")
public class JaxbUser {
    @XmlAttribute(name = "guid")
    private String guid;

    public JaxbUser() {
    }

    public JaxbUser(final String guid) {
        this.guid = guid;
    }
}
