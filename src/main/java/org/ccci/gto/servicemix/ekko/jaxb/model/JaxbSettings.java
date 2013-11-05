package org.ccci.gto.servicemix.ekko.jaxb.model;

import static org.ccci.gto.servicemix.ekko.model.Course.ENROLLMENT_APPROVAL;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.ccci.gto.servicemix.ekko.model.Course;

@XmlRootElement(name = "settings")
public class JaxbSettings {
    @XmlAttribute(name = "id")
    private long id;

    @XmlElement(name = "enrollmentType")
    private String enrollmentType = ENROLLMENT_APPROVAL;

    @XmlElement(name = "public")
    private boolean publicCourse = false;

    public JaxbSettings() {
    }

    public JaxbSettings(final Course course) {
        this.id = course.getId();
        this.publicCourse = course.isPublic();
        this.enrollmentType = course.getEnrollment();
    }
}
