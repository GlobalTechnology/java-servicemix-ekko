package org.ccci.gto.servicemix.ekko.jaxb.model;

import static org.ccci.gto.servicemix.ekko.Constants.XMLNS_EKKO;

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
            if (manifest != null) {
                try {
                    this.schemaVersion = Long.valueOf(manifest.getDocumentElement().getAttributeNS(null,
                            "schemaVersion"));
                    this.meta = JaxbDomElements.fromXPath(manifest, "/ekko:course/ekko:meta/*");
                    this.resources = JaxbDomElements.fromXPath(manifest, "/ekko:course/ekko:resources/*");
                } catch (final Exception e) {
                    this.schemaVersion = 0;
                    this.meta = null;
                    this.resources = null;
                }
            }
        }
    }
}
