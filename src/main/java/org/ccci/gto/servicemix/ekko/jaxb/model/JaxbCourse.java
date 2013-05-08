package org.ccci.gto.servicemix.ekko.jaxb.model;

import static org.ccci.gto.servicemix.ekko.Constants.XMLNS_EKKO;

import java.net.URI;

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
        this.id = course.getId();
        this.version = course.getVersion();
        this.title = course.getTitle();

        if (parseManifest) {
            final Document manifest = DomUtils.parse(course.getManifest());
            this.meta = JaxbDomElements.fromXPath(manifest, "/ekko:course/ekko:meta/*");
            this.resources = JaxbDomElements.fromXPath(manifest, "/ekko:course/ekko:resources/*");
        }
    }

    public void setUri(final URI uri) {
        this.uri = uri;
    }
}
