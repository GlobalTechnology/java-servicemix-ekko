package org.ccci.gto.servicemix.ekko.jaxb.model;

import static org.ccci.gto.servicemix.ekko.jaxrs.api.Constants.PARAM_RESOURCE_SHA1;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.core.UriBuilder;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import org.ccci.gto.servicemix.ekko.model.Resource;

@XmlRootElement(name = "resource")
public class JaxbResource extends JaxbAbstractResource {
    @XmlAttribute(name = "sha1")
    private String sha1;

    @XmlAttribute(name = "size")
    private long size = 0;

    @XmlAttribute(name = "uri")
    private URI uri;

    public JaxbResource() {
    }

    public JaxbResource(final Resource resource) {
        this(resource, null, null);
    }

    public JaxbResource(final Resource resource, final UriBuilder uri, final Map<String, Object> uriValues) {
        super(resource);
        this.sha1 = resource.getSha1();
        this.size = resource.getSize();

        if (uri != null) {
            final Map<String, Object> values = new HashMap<String, Object>();
            if (uriValues != null) {
                values.putAll(uriValues);
            }
            values.put(PARAM_RESOURCE_SHA1, resource.getSha1());
            this.uri = uri.buildFromMap(values);
        }
    }

    public URI getUri() {
        return this.uri;
    }
}
