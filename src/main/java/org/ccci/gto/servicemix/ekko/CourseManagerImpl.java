package org.ccci.gto.servicemix.ekko;

import static org.ccci.gto.servicemix.ekko.Constants.XMLNS_EKKO;
import static org.ccci.gto.servicemix.ekko.model.Course.ENROLLMENT_APPROVAL;
import static org.ccci.gto.servicemix.ekko.model.Course.ENROLLMENT_OPEN;

import org.apache.commons.lang.StringUtils;
import org.ccci.gto.servicemix.ekko.model.Course;
import org.ccci.gto.servicemix.ekko.model.Course.CourseQuery;
import org.ccci.gto.servicemix.ekko.model.FileResource;
import org.ccci.gto.servicemix.ekko.model.Resource;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.persistence.EntityExistsException;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class CourseManagerImpl implements CourseManager {
    private static final SecureRandom RAND = new SecureRandom();

    @PersistenceContext
    private EntityManager em;

    @Override
    @Transactional
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
        course.setPending(null);
        course.setPublic(false);
        course.setPendingManifest(null);
        this.em.flush();

        // return success
        return true;
    }

    @Override
    @Transactional
    public Course publishCourse(final CourseQuery courseQuery) throws CourseNotFoundException, ManifestException {
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
        final List<ManifestException> exceptions = new ArrayList<>();
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
        for (final Resource resource : course.getVideoResources()) {
            resource.setPublished(false);
            resource.setMetaResource(false);
        }

        // mark published resources
        for (final Element element : DomUtils.getElements(manifest,
                "/ekko:course/ekko:resources//ekko:resource[@type='file' or @type='ecv']")) {
            final Resource resource;
            switch (element.getAttribute("type")) {
            case "file":
                resource = course.getResource(element.getAttribute("sha1"));
                break;
            case "ecv":
                resource = course.getVideoResource(Long.valueOf(element.getAttribute("videoId")));
                break;
            default:
                continue;
            }

            if (resource != null) {
                resource.setPublished(true);
            }
        }

        // mark meta resources
        for (final Element element : DomUtils.getElements(manifest,
                "/ekko:course/ekko:resources//ekko:resource[@id=/ekko:course/ekko:meta//@resource]"
                        + "/descendant-or-self::ekko:resource[@type='file' or @type='ecv']")) {
            final Resource resource;
            switch (element.getAttribute("type")) {
            case "file":
                resource = course.getResource(element.getAttribute("sha1"));
                break;
            case "ecv":
                resource = course.getVideoResource(Long.valueOf(element.getAttribute("videoId")));
                break;
            default:
                continue;
            }

            if (resource != null) {
                resource.setMetaResource(true);
            }
        }

        // replace the manifest with the pending manifest
        course.setManifest(pendingManifest);
        course.setPendingManifest(null);

        // update course meta-data
        try {
            course.setTitle((String) DomUtils.compileXPath("/ekko:course/ekko:meta/ekko:title").evaluate(manifest,
                    XPathConstants.STRING));
        } catch (final XPathExpressionException ignored) {
            course.setTitle(null);
        }

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
        for (final Resource resource : course.getVideoResources()) {
            resource.setPublished(false);
            resource.setMetaResource(false);
        }

        // increment the version of the course
        course.incVersion();

        return course;
    }

    @Override
    @Transactional(readOnly = true)
    public Course getCourse(final Long id) {
        return this.em.find(Course.class, id);
    }

    @Override
    @Transactional(readOnly = true)
    public Course getCourse(final CourseQuery query) {
        final List<Course> courses = this.getCourses(query);
        if (courses.size() > 0) {
            return courses.get(0);
        }

        return null;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Course> getCourses(final CourseQuery query) {
        return query.execute(this.em);
    }

    @Override
    @Transactional
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

    @Override
    @Transactional
    public Course updateCourseEnrolled(final CourseQuery query, final Collection<String> toAdd,
            final Collection<String> toRemove) throws CourseNotFoundException {
        // short-circuit if a valid course couldn't be found
        final Course course = this.getCourse(query.clone().loadEnrolled().loadPending());
        if (course == null) {
            throw new CourseNotFoundException();
        }

        // remove enrolled, then add new enrolled
        course.removeEnrolled(toRemove);
        course.addEnrolled(toAdd);

        // filter all enrolled users out of pending users
        course.removePending(course.getEnrolled());

        // return the updated course
        return course;
    }

    @Override
    @Transactional
    public Course updateCoursePending(final CourseQuery query, final Collection<String> toAdd,
            final Collection<String> toRemove) throws CourseNotFoundException {
        // short-circuit if a valid course couldn't be found
        final Course course = this.getCourse(query.clone().loadEnrolled().loadPending());
        if (course == null) {
            throw new CourseNotFoundException();
        }

        // remove enrolled, then add new enrolled
        course.removePending(toRemove);
        course.addPending(toAdd);

        // filter all enrolled users out of pending users
        course.removePending(course.getEnrolled());

        // return the updated course
        return course;
    }

    @Override
    @Transactional
    public Course enroll(final CourseQuery query, final String guid) throws CourseNotFoundException {
        // short-circuit if a valid course couldn't be found
        final Course course = this.getCourse(query.clone().loadEnrolled().loadPending());
        if (course == null) {
            throw new CourseNotFoundException();
        }

        // determine what we do based on enrollment model
        switch (course.getEnrollment()) {
        case ENROLLMENT_OPEN:
            course.addEnrolled(guid);
            break;
        case ENROLLMENT_APPROVAL:
            // admins should immediately be enrolled
            if (course.isAdmin(guid)) {
                course.addEnrolled(guid);
            } else {
                course.addPending(guid);
            }
            break;
        }

        // filter all enrolled users out of pending users
        course.removePending(course.getEnrolled());

        // return the course
        return course;
    }

    @Override
    @Transactional
    public Course unenroll(final CourseQuery query, final String guid) throws CourseNotFoundException {
        // short-circuit if a valid course couldn't be found
        final Course course = this.getCourse(query.clone().loadEnrolled().loadPending());
        if (course == null) {
            throw new CourseNotFoundException();
        }

        // remove the specified user from enrolled and pending enrollment
        course.removeEnrolled(guid);
        course.removePending(guid);

        // return the course
        return course;
    }

    @Override
    @Transactional(readOnly = true)
    public FileResource getResource(final FileResource.PrimaryKey key) {
        return this.em.find(FileResource.class, key);
    }

    @Transactional
    @Override
    public FileResource storeResource(final Course course, final FileResource resource) {
        this.em.persist(resource);
        return resource;
    }

    @Transactional
    @Override
    public void removeResource(final FileResource resource) {
        this.em.remove(this.em.merge(resource));
    }

    private void validateManifest(final Course course, final Document manifest) throws ManifestException {
        // capture multiple ManifestExceptions
        final List<ManifestException> exceptions = new ArrayList<>();

        // validate the actual manifest xml
        try {
            DomUtils.validate(manifest);
        } catch (final SAXException e) {
            exceptions.add(new ManifestException("invalid manifest xml", e));
        }

        // validate all resources
        final List<Element> resources = DomUtils.getElements(manifest, "/ekko:course/ekko:resources//ekko:resource");
        for (final Element resource : resources) {
            final String type = resource.getAttribute("type");
            if (type != null) {
                switch (type) {
                case "dynamic":
                    // make sure multi resources have at least 1 bundled resource
                    if (resource.getElementsByTagNameNS(XMLNS_EKKO, "resource").getLength() == 0) {
                        // TODO: specific exception?
                        exceptions.add(new ManifestException("Dynamic resource has no bundled resources"));
                    }
                    break;
                case "file":
                    // ensure standard resources have been uploaded to the course
                    final String sha1 = resource.getAttribute("sha1");
                    if (course.getResource(sha1) == null) {
                        exceptions.add(new MissingFileResourceManifestException(sha1));
                    }
                    break;
                case "ecv":
                    // ensure we have a valid videoId
                    final String rawId = resource.getAttribute("videoId");
                    final long videoId;
                    try {
                        videoId = Long.parseLong(rawId);
                    } catch (final NumberFormatException e) {
                        exceptions.add(new ManifestException("Invalid videoId format: " + rawId));
                        break;
                    }

                    if (course.getVideoResource(videoId) == null) {
                        exceptions.add(new MissingVideoResourceManifestException(videoId));
                    }
                    break;
                case "arclight":
                    // ensure we have a valid refId
                    final String refId = resource.getAttribute("refId");
                    if (StringUtils.isBlank(refId)) {
                        exceptions.add(new ManifestException("Invalid refId: " + refId));
                    }
                    break;
                case "uri":
                    // TODO
                    break;
                default:
                    exceptions.add(new ManifestException("unrecognized resource type"));
                    break;
                }
            } else {
                exceptions.add(new ManifestException("unrecognized resource type"));
            }
        }

        // throw any errors we encountered
        if (exceptions.size() > 0) {
            throw new MultipleManifestExceptions(exceptions);
        }
    }
}
