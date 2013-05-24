package org.ccci.gto.servicemix.ekko.jaxrs.api;

import org.ccci.gto.servicemix.ekko.CourseManager;
import org.ccci.gto.servicemix.ekko.DomUtils;
import org.ccci.gto.servicemix.ekko.model.Course;
import org.ccci.gto.servicemix.ekko.model.Course.CourseQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Document;

public class CourseApiSupport implements CourseApi.Support {
    @Autowired
    private CourseManager courseManager;

    public void setCourseManager(final CourseManager courseManager) {
        this.courseManager = courseManager;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Override
    public Course updateManifest(final CourseQuery query, final Document manifest) {
        // retrieve the specified course
        final Course course = this.courseManager.getCourse(query);

        if (course != null) {
            course.setPendingManifest(DomUtils.asString(manifest));
        }

        return course;
    }
}
