package org.ccci.gto.servicemix.ekko.jaxb.model;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlSeeAlso;

import org.ccci.gto.servicemix.ekko.model.AbstractResource;

@XmlRootElement
@XmlSeeAlso({ JaxbFileResource.class, JaxbVideoResource.class })
public abstract class JaxbAbstractResource {
    @XmlAttribute(name = "published")
    private boolean published = false;

    public JaxbAbstractResource() {
    }

    public JaxbAbstractResource(final AbstractResource resource) {
        if (resource != null) {
            this.published = resource.isPublished();
        }
    }
}
