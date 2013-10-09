package org.ccci.gto.servicemix.ekko.jaxb.model;

import static org.ccci.gto.servicemix.ekko.Constants.XMLNS_EKKO;
import static org.ccci.gto.servicemix.ekko.jaxrs.api.Constants.PARAM_COURSE;

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

    @XmlAttribute(name = "schemaVersion")
    private long schemaVersion = 0;

    @XmlAttribute(name = "title")
    private String title;

    @XmlAttribute(name = "uri")
    private URI uri;

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
        this(course, parseManifest, null, null);
    }

    public JaxbCourse(final Course course, final boolean parseManifest, final UriBuilder uri,
            final Map<String, Object> uriValues) {
        this.id = course.getId();
        this.version = course.getVersion();
        this.title = course.getTitle();

        if (parseManifest) {
            final Document manifest = DomUtils.parse(course.getManifest());
            if (manifest != null) {
                try {
                    this.schemaVersion = Long.valueOf(manifest.getDocumentElement().getAttributeNS(null,
                            "schemaVersion"));
                    this.meta = JaxbDomElements.fromXPath(manifest, "/ekko:course/ekko:meta/*");
                    this.resources = JaxbDomElements.fromXPath(manifest, "/ekko:course/ekko:resources/*");
                } catch (final Exception e) {
                    this.schemaVersion = 0;
                }
            }
        }

        if (uri != null) {
            final Map<String, Object> values = new HashMap<String, Object>();
            if (uriValues != null) {
                values.putAll(uriValues);
            }

            values.put(PARAM_COURSE, course.getId());

            if (uri != null) {
                this.uri = uri.buildFromMap(values);
            }
        }
    }

    public URI getUri() {
        return uri;
    }
}
