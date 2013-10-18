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

    @XmlAttribute(name = "public")
    private boolean publicCourse = false;

    @XmlAttribute(name = "enrollmentType")
    private String enrollmentType;

    @XmlElement(name = "permission")
    private JaxbPermission permission;

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
        this.enrollmentType = course.getEnrollment();

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
            this.permission = new JaxbPermission();
            this.permission.guid = guid;
            this.permission.admin = course.isAdmin(guid);
            this.permission.enrolled = course.isEnrolled(guid);
            this.permission.pending = course.isPending(guid);
            this.permission.contentVisible = course.isContentVisibleTo(guid);
        }
    }

    @XmlRootElement
    public static class JaxbPermission {
        @XmlAttribute(name = "guid")
        private String guid;

        @XmlAttribute(name = "admin")
        private Boolean admin = null;

        @XmlAttribute(name = "enrolled")
        private Boolean enrolled = null;

        @XmlAttribute(name = "pending")
        private Boolean pending = null;

        @XmlAttribute(name = "contentVisible")
        private Boolean contentVisible = null;
    }
}
