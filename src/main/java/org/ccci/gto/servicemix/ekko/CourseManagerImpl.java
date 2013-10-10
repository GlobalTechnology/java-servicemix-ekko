package org.ccci.gto.servicemix.ekko;

import static org.ccci.gto.servicemix.ekko.Constants.XMLNS_EKKO;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
import org.xml.sax.SAXException;

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

    @Override
    @Transactional
    public boolean deleteCourse(final CourseQuery query) throws CourseNotFoundException {
        // short-circuit if a valid course couldn't be found
        final Course course = this.getCourse(query.clone().loadResources(true));
        if (course == null) {
            throw new CourseNotFoundException();
        }

        // short-circuit if there are still resources in this course
        if (course.getResources().size() > 0) {
            // TODO: should this be an exception?
            return false;
        }

        // remove all meta-data, but keep course to prevent future id collisions
        course.setAdmins(null);
        course.setEnrolled(null);
        course.setPublic(false);
        course.setPendingManifest(null);
        this.em.flush();

        // return success
        return true;
    }

    @Transactional
    @Override
    public Course publishCourse(final CourseQuery courseQuery) throws CourseNotFoundException, ManifestException,
            MultipleManifestExceptions {
        // short-circuit if a valid course couldn't be found
        final Course course = this.getCourse(courseQuery.clone().loadManifest().loadPendingManifest());
        if (course == null) {
            throw new CourseNotFoundException();
        }

        // don't do anything if there isn't a pending manifest
        final String pendingManifest = course.getPendingManifest();
        if (pendingManifest == null) {
            throw new ManifestException("no pending manifest");
        }

        // parse the manifest
        final Document manifest = DomUtils.parse(pendingManifest);
        if (manifest == null) {
            throw new ManifestException("error parsing the pending manifest");
        }

        // validate pending manifest
        final List<ManifestException> exceptions = new ArrayList<ManifestException>();
        try {
            this.validateManifest(course, manifest);
        } catch (final MultipleManifestExceptions e) {
            exceptions.addAll(e.getExceptions());
        } catch (final ManifestException e) {
            exceptions.add(e);
        } catch (final Exception e) {
            exceptions.add(new ManifestException("Unexpected error", e));
        }

        // throw any errors we encountered
        if (exceptions.size() > 0) {
            throw new MultipleManifestExceptions(exceptions);
        }

        // reset published state of all resources
        // XXX OPTIMIZATION: reset all flags with single update query
        for (final Resource resource : course.getResources()) {
            resource.setPublished(false);
            resource.setMetaResource(false);
        }

        // mark published resources
        for (final Element element : DomUtils.getElements(manifest,
                "/ekko:course/ekko:resources//ekko:resource[@type='file']")) {
            final Resource resource = course.getResource(element.getAttribute("sha1"));
            if (resource != null) {
                resource.setPublished(true);
            }
        }

        // mark meta resources
        for (final Element element : DomUtils.getElements(manifest,
                "/ekko:course/ekko:resources//ekko:resource[@id=/ekko:course/ekko:meta//@resource]"
                        + "/descendant-or-self::ekko:resource[@type='file']")) {
            final Resource resource = course.getResource(element.getAttribute("sha1"));
            if (resource != null) {
                resource.setMetaResource(true);
            }
        }

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

    @Override
    @Transactional
    public Course unpublishCourse(final CourseQuery courseQuery) throws CourseNotFoundException {
        // short-circuit if a valid course couldn't be found
        final Course course = this.getCourse(courseQuery.clone().loadManifest().loadResources());
        if (course == null) {
            throw new CourseNotFoundException();
        }

        // short-circuit if the course is not currently published, we don't need
        // to throw an exception
        if (!course.isPublished()) {
            return course;
        }

        // move the manifest to the pending manifest field
        course.setPendingManifest(course.getManifest());
        course.setManifest(null);

        // reset published state of all resources
        // XXX OPTIMIZATION: reset all flags with single update query
        for (final Resource resource : course.getResources()) {
            resource.setPublished(false);
            resource.setMetaResource(false);
        }

        // remove any previously generated zip file
        course.setZip(null);

        // increment the version of the course
        course.incVersion();

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
            final Collection<String> toRemove) throws CourseNotFoundException {
        // short-circuit if a valid course couldn't be found
        final Course course = this.getCourse(query.clone());
        if (course == null) {
            throw new CourseNotFoundException();
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
            final Collection<String> toRemove) throws CourseNotFoundException {
        // short-circuit if a valid course couldn't be found
        final Course course = this.getCourse(query.clone().loadEnrolled(true));
        if (course == null) {
            throw new CourseNotFoundException();
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

    private void validateManifest(final Course course, final Document manifest) throws ManifestException,
            MultipleManifestExceptions {
        // capture multiple ManifestExceptions
        final List<ManifestException> exceptions = new ArrayList<ManifestException>();

        // validate the actual manifest xml
        try {
            DomUtils.validate(manifest);
        } catch (final SAXException e) {
            exceptions.add(new ManifestException("invalid manifest xml", e));
        }

        // validate all resources
        final List<Element> resources = DomUtils.getElements(manifest, "/ekko:course/ekko:resources//ekko:resource");
        final Set<String> ids = new HashSet<String>();
        for (final Element resource : resources) {
            final String type = resource.getAttribute("type");
            if ("dynamic".equals(type)) {
                // make sure multi resources have at least 1 bundled resource
                if (resource.getElementsByTagNameNS(XMLNS_EKKO, "resource").getLength() == 0) {
                    // TODO: specific exception?
                    exceptions.add(new ManifestException("Dynamic resource has no bundled resources"));
                }
            } else if ("file".equals(type)) {
                // ensure standard resources have been uploaded to the course
                final String sha1 = resource.getAttribute("sha1");
                if (course.getResource(sha1) == null) {
                    exceptions.add(new MissingResourceManifestException(sha1));
                }
            } else if ("uri".equals(type)) {
                // TODO
            } else {
                exceptions.add(new ManifestException("unrecognized resource type"));
            }

            // store the resource id
            if (resource.hasAttribute("id")) {
                ids.add(resource.getAttribute("id"));
            }
        }

        // throw any errors we encountered
        if (exceptions.size() > 0) {
            throw new MultipleManifestExceptions(exceptions);
        }
    }
}
