package org.ccci.gto.servicemix.ekko;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.persistence.EntityExistsException;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.ccci.gto.servicemix.ekko.model.Course;
import org.ccci.gto.servicemix.ekko.model.Course.CourseQuery;
import org.ccci.gto.servicemix.ekko.model.Resource;
import org.ccci.gto.servicemix.ekko.model.ResourcePrimaryKey;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class CourseManagerImpl implements CourseManager {
    private static final SecureRandom RAND = new SecureRandom();

    @PersistenceContext
    private EntityManager em;

    @Transactional
    @Override
    public Course createCourse(final Course course) {
        while (true) {
            try {
                // generate a new id for this course
                long id = 0;
                while (id <= 0) {
                    id = RAND.nextLong();
                }
                course.setId(id);

                // save this course
                this.em.persist(course);
            } catch (final EntityExistsException e) {
                // a course with the specified id exists, try again
                continue;
            }

            // we created the course successfully, break processing
            break;
        }

        return course;
    }

    @Transactional
    @Override
    public Course publishCourse(final CourseQuery courseQuery) {
        final List<Exception> errors = new ArrayList<Exception>();

        // short-circuit if a valid course couldn't be found
        final Course course = this.getCourse(courseQuery.clone().loadManifest(true).loadPendingManifest(true));
        if (course == null) {
            // TODO: maybe throw an exception instead?
            return null;
        }

        // don't do anything if there isn't a pending manifest
        final String pendingManifest = course.getPendingManifest();
        if (course.getPendingManifest() == null) {
            // TODO: maybe throw an exception instead?
            return course;
        }

        // validate pending manifest
        final Document manifest = DomUtils.parse(pendingManifest);

        // make sure we have all the resources listed in the manifest
        final Map<String, Resource> resources = new HashMap<String, Resource>();
        for (final Element resourceNode : DomUtils.getElements(manifest, "/ekko:course/ekko:resources/ekko:resource")) {
            final Resource resource = course.getResource(resourceNode.getAttribute("sha1"));
            resources.put(resourceNode.getAttribute("id"), resource);
        }

        // TODO: finish manifest validation

        // throw any errors we encountered

        // reset published state of all resources
        // XXX OPTIMIZATION: reset all flags with single update query
        for (final Resource resource : course.getResources()) {
            resource.setPublished(false);
            resource.setMetaResource(false);
        }

        // TODO: mark published resources

        // TODO: mark meta resources

        // replace the manifest with the pending manifest
        course.setManifest(pendingManifest);
        course.setPendingManifest(null);

        // remove any previously generated zip file
        course.setZip(null);

        // increment the version of the course
        course.incVersion();

        // return the updated course
        return course;
    }

    @Transactional
    @Override
    public Course getCourse(final Long id) {
        return this.em.find(Course.class, id);
    }

    @Transactional
    @Override
    public Course getCourse(final CourseQuery query) {
        final List<Course> courses = this.getCourses(query);
        if (courses.size() > 0) {
            return courses.get(0);
        }

        return null;
    }

    @Transactional
    @Override
    public List<Course> getCourses(final CourseQuery query) {
        return query.execute(this.em);
    }

    @Transactional
    @Override
    public Course updateCourseAdmins(final CourseQuery query, final Collection<String> toAdd,
            final Collection<String> toRemove) {
        // short-circuit if a valid course couldn't be found
        final Course course = this.getCourse(query.clone());
        if (course == null) {
            // TODO: maybe throw an exception instead?
            return null;
        }

        // remove admins, then add new admins
        course.removeAdmins(toRemove);
        course.addAdmins(toAdd);

        // return the updated course
        return course;
    }

    @Transactional
    @Override
    public Course updateCourseEnrolled(final CourseQuery query, final Collection<String> toAdd,
            final Collection<String> toRemove) {
        // short-circuit if a valid course couldn't be found
        final Course course = this.getCourse(query.clone());
        if (course == null) {
            // TODO: maybe throw an exception instead?
            return null;
        }

        // remove enrolled, then add new enrolled
        course.removeEnrolled(toRemove);
        course.addEnrolled(toAdd);

        // return the updated course
        return course;
    }

    @Transactional
    @Override
    public Resource getResource(final ResourcePrimaryKey key) {
        return this.em.find(Resource.class, key);
    }

    @Transactional
    @Override
    public Resource storeCourseZip(final Course course, final Resource resource) {
        final Course dbCourse = this.em.find(Course.class, course.getId());

        if (dbCourse.getZipSha1() == null) {
            this.em.persist(resource);
            dbCourse.setZip(resource);
            return resource;
        }

        return null;
    }

    @Transactional
    @Override
    public Resource storeResource(final Course course, final Resource resource) {
        this.em.persist(resource);
        return resource;
    }

    @Transactional
    @Override
    public void removeResource(final Resource resource) {
        this.em.remove(this.em.merge(resource));
    }
}
