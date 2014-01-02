package org.ccci.gto.servicemix.ekko.jaxb.model;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlSeeAlso;

import org.ccci.gto.servicemix.ekko.model.Resource;

@XmlRootElement
@XmlSeeAlso({ JaxbFileResource.class, JaxbVideoResource.class })
public abstract class JaxbResource {
    @XmlAttribute(name = "published")
    private boolean published = false;

    public JaxbResource() {
    }

    public JaxbResource(final Resource resource) {
        if (resource != null) {
            this.published = resource.isPublished();
        }
    }
}
