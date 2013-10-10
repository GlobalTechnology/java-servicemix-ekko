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

    @XmlAttribute(name = "admin")
    private Boolean admin = null;

    @XmlAttribute(name = "contentVisible")
    private Boolean contentVisible = null;

    @XmlAttribute(name = "public")
    private boolean publicCourse = false;

    @XmlElement(name = "enrollment")
    private JaxbEnrollment enrollment;

    @XmlElement(namespace = XMLNS_EKKO, name = "meta")
    private JaxbDomElements meta;

    @XmlElement(namespace = XMLNS_EKKO, name = "resources")
    private JaxbDomElements resources;

    public JaxbCourse() {
    }

    public JaxbCourse(final Course course, final boolean parseManifest, final String guid) {
        this.id = course.getId();
        this.version = course.getVersion();
        this.publicCourse = course.isPublic();
        this.enrollment = new JaxbEnrollment();
        this.enrollment.type = course.getEnrollment();

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

        if (guid != null) {
            this.contentVisible = course.isContentVisibleTo(guid);
            this.admin = course.isAdmin(guid);
            this.enrollment.enrolled = course.isEnrolled(guid);
            this.enrollment.pending = course.isPending(guid);
        }
    }

    @XmlRootElement
    public static class JaxbEnrollment {
        @XmlAttribute(name = "type")
        private String type;

        @XmlAttribute(name = "enrolled")
        private Boolean enrolled = null;

        @XmlAttribute(name = "pending")
        private Boolean pending = null;
    }
}
