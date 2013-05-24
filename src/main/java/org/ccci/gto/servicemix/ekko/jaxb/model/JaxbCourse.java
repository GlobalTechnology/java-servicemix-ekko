package org.ccci.gto.servicemix.ekko.jaxb.model;

import static org.ccci.gto.servicemix.ekko.Constants.XMLNS_EKKO;
import static org.ccci.gto.servicemix.ekko.jaxrs.api.Constants.PARAM_COURSE;
import static org.ccci.gto.servicemix.ekko.jaxrs.api.Constants.PARAM_RESOURCE_SHA1;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.core.UriBuilder;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.ccci.gto.servicemix.ekko.DomUtils;
import org.ccci.gto.servicemix.ekko.model.Course;
import org.w3c.dom.Document;

@XmlRootElement(name = "course")
public class JaxbCourse {
    @XmlAttribute(name = "id")
    private Long id;

    @XmlAttribute(name = "version")
    private Long version;

    @XmlAttribute(name = "title")
    private String title;

    @XmlAttribute(name = "uri")
    private URI uri;

    @XmlAttribute(name = "zipUri")
    private URI zipUri;

    @XmlElement(namespace = XMLNS_EKKO, name = "meta")
    private JaxbDomElements meta;

    @XmlElement(namespace = XMLNS_EKKO, name = "resources")
    private JaxbDomElements resources;

    public JaxbCourse() {
    }

    public JaxbCourse(final Course course) {
        this(course, true);
    }

    public JaxbCourse(final Course course, final boolean parseManifest) {
        this(course, parseManifest, null, null, null);
    }

    public JaxbCourse(final Course course, final boolean parseManifest, final UriBuilder uri,
            final Map<String, Object> uriValues) {
        this(course, parseManifest, uri, null, uriValues);
    }

    public JaxbCourse(final Course course, final boolean parseManifest, final UriBuilder uri,
            final UriBuilder resourceUri, final Map<String, Object> uriValues) {
        this.id = course.getId();
        this.version = course.getVersion();
        this.title = course.getTitle();

        if (parseManifest) {
            final Document manifest = DomUtils.parse(course.getManifest());
            this.meta = JaxbDomElements.fromXPath(manifest, "/ekko:course/ekko:meta/*");
            this.resources = JaxbDomElements.fromXPath(manifest, "/ekko:course/ekko:resources/*");
        }

        if (uri != null || resourceUri != null) {
            final Map<String, Object> values = new HashMap<String, Object>();
            if (uriValues != null) {
                values.putAll(uriValues);
            }

            values.put(PARAM_COURSE, course.getId());

            if (uri != null) {
                this.uri = uri.buildFromMap(values);
            }
            final String zipSha1 = course.getZipSha1();
            if (resourceUri != null && zipSha1 != null) {
                values.put(PARAM_RESOURCE_SHA1, zipSha1);
                this.zipUri = resourceUri.buildFromMap(values);
            }
        }
    }

    public URI getUri() {
        return uri;
    }
}
