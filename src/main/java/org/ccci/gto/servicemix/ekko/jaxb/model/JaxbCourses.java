package org.ccci.gto.servicemix.ekko.jaxb.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "courses")
public class JaxbCourses {
    @XmlElementRef
    private List<JaxbCourse> courses = new ArrayList<JaxbCourse>();

    public void addCourse(final JaxbCourse course) {
        this.courses.add(course);
    }

    public void setCourses(final Collection<JaxbCourse> courses) {
        this.courses.clear();
        if (courses != null) {
            this.courses.addAll(courses);
        }
    }
}
