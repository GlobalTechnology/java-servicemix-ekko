package org.ccci.gto.servicemix.ekko.jaxb.model;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "courses")
public class JaxbCourses {
    @XmlElementRef
    private List<JaxbCourse> courses = new ArrayList<JaxbCourse>();

    @XmlAttribute
    private int start = 0;
    @XmlAttribute
    private int limit = 0;
    @XmlAttribute
    private boolean hasMore = false;
    @XmlAttribute
    private URI moreUri = null;

    public void addCourse(final JaxbCourse course) {
        this.courses.add(course);
    }

    public void setCourses(final Collection<JaxbCourse> courses) {
        this.courses.clear();
        if (courses != null) {
            this.courses.addAll(courses);
        }
    }

    public void setStart(final int start) {
        this.start = start;
    }

    public void setLimit(final int limit) {
        this.limit = limit;
    }

    public void setMoreUri(final URI moreUri) {
        this.hasMore = true;
        this.moreUri = moreUri;
    }
}
