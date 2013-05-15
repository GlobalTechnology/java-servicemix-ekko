package org.ccci.gto.servicemix.ekko.jaxb.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "resources")
public class JaxbResources {
    @XmlElementRef
    private List<JaxbResource> resources = new ArrayList<JaxbResource>();

    public void addResource(final JaxbResource resource) {
        this.resources.add(resource);
    }

    public void setResources(final Collection<JaxbResource> resources) {
        this.resources.clear();
        if (resources != null) {
            this.resources.addAll(resources);
        }
    }
}
