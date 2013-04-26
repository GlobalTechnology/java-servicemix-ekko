package org.ccci.gto.servicemix.ekko.jaxrs.api;

import java.util.List;

import org.ccci.gto.servicemix.ekko.CourseManager;
import org.ccci.gto.servicemix.ekko.model.Course;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public class CoursesApiSupport implements CoursesApi.Support {
    @Autowired
    private CourseManager courseManager;

    public void setCourseManager(final CourseManager courseManager) {
        this.courseManager = courseManager;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Override
    public List<Course> getCourses(final String guid) {
        return this.courseManager.getCourses(true);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Override
    public Course getCourse(final Long id) {
        final Course course = this.courseManager.getCourse(id);

        // load the manifest before closing transaction
        if (course != null) {
            course.getManifest();
        }

        return course;
    }
}
