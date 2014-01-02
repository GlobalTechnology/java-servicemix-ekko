package org.ccci.gto.servicemix.ekko.jaxb.model;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import org.ccci.gto.servicemix.ekko.model.VideoResource;

@XmlRootElement(name = "video")
public class JaxbVideoResource extends JaxbAbstractResource {
    @XmlAttribute(name = "id")
    private long id;

    public JaxbVideoResource() {
    }

    public JaxbVideoResource(final VideoResource resource) {
        super(resource);
        if (resource != null) {
            this.id = resource.getVideoId();
        }
    }
}
