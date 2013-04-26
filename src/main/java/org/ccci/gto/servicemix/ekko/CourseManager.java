package org.ccci.gto.servicemix.ekko;

import java.util.List;

import org.ccci.gto.servicemix.ekko.model.Course;

public interface CourseManager {
    List<Course> getCourses();

    List<Course> getCourses(boolean includeManifest);

    Course getCourse(Long id);
}
