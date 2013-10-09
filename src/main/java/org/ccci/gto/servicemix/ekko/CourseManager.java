package org.ccci.gto.servicemix.ekko;

import java.util.Collection;
import java.util.List;

import org.ccci.gto.servicemix.ekko.model.Course;
import org.ccci.gto.servicemix.ekko.model.Course.CourseQuery;
import org.ccci.gto.servicemix.ekko.model.Resource;
import org.ccci.gto.servicemix.ekko.model.ResourcePrimaryKey;

public interface CourseManager {
    Course createCourse(Course course);

    Course getCourse(Long id);

    Course getCourse(CourseQuery courseQuery);

    List<Course> getCourses(CourseQuery courseQuery);

    Course publishCourse(CourseQuery courseQuery) throws CourseNotFoundException, ManifestException;

    Course unpublishCourse(CourseQuery courseQuery) throws CourseNotFoundException;

    Course updateCourseAdmins(CourseQuery courseQuery, Collection<String> toAdd, Collection<String> toRemove)
            throws CourseNotFoundException;

    Course updateCourseEnrolled(CourseQuery courseQuery, Collection<String> toAdd, Collection<String> toRemove)
            throws CourseNotFoundException;

    boolean deleteCourse(CourseQuery query) throws CourseNotFoundException;

    Resource getResource(ResourcePrimaryKey key);

    Resource storeResource(Course course, Resource resource);

    void removeResource(Resource resource);

    Resource storeCourseZip(Course course, Resource resource);
}
