package org.ccci.gto.servicemix.ekko.jaxrs.api;

import static javax.ws.rs.core.MediaType.APPLICATION_XML;
import static org.ccci.gto.servicemix.common.jaxrs.api.Constants.PATH_SESSION;
import static org.ccci.gto.servicemix.ekko.jaxrs.api.Constants.PARAM_RESOURCE_SHA1;
import static org.ccci.gto.servicemix.ekko.jaxrs.api.Constants.PATH_COURSE;

import java.io.InputStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

import org.ccci.gto.servicemix.common.model.Session;
import org.ccci.gto.servicemix.ekko.CourseNotFoundException;
import org.ccci.gto.servicemix.ekko.DomUtils;
import org.ccci.gto.servicemix.ekko.ManifestException;
import org.ccci.gto.servicemix.ekko.MultipleManifestExceptions;
import org.ccci.gto.servicemix.ekko.ResourceManager;
import org.ccci.gto.servicemix.ekko.jaxb.model.JaxbAdmins;
import org.ccci.gto.servicemix.ekko.jaxb.model.JaxbCourse;
import org.ccci.gto.servicemix.ekko.jaxb.model.JaxbEnrolled;
import org.ccci.gto.servicemix.ekko.jaxb.model.JaxbError;
import org.ccci.gto.servicemix.ekko.jaxb.model.JaxbErrors;
import org.ccci.gto.servicemix.ekko.model.Course;
import org.ccci.gto.servicemix.ekko.model.Course.CourseQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.w3c.dom.Document;

@Path(PATH_SESSION + "/courses/" + PATH_COURSE)
public class CourseApi extends AbstractApi {
    /**
     * API Support functions used for loading data
     */
    public interface Support {
        Course updateManifest(CourseQuery courseQuery, Document manifest);
    }

    // @Autowired
    private Support support;

    public void setSupport(final Support support) {
        this.support = support;
    }

    @Autowired
    private ResourceManager resourceManager;

    public void setResourceManager(final ResourceManager resourceManager) {
        this.resourceManager = resourceManager;
    }

    /**
     * Retrieves the course meta-data for a specified course
     * 
     * @param uri
     * @return
     */
    @GET
    @Produces(APPLICATION_XML)
    public Response getCourse(@Context final UriInfo uri) {
        // validate the session
        final Session session = this.getSession(uri);
        if (session == null || session.isExpired()) {
            return Response.status(Status.UNAUTHORIZED).build();
        }

        // retrieve course
        final String guid = session.getGuid();
        final Course course = this.courseManager.getCourse(this.getCourseQuery(uri).admin(guid).enrolled(guid)
                .publicCourse(true).loadManifest(true));
        if (course != null) {
            final JaxbCourse jaxbCourse = new JaxbCourse(course, true, this.getCourseUriBuilder(uri),
                    this.getResourceUriBuilder(uri), this.getUriValues(uri));
            return Response.ok(jaxbCourse).build();
        }

        // return not found because a valid course wasn't found
        return Response.status(Status.NOT_FOUND).build();
    }

    @GET
    @Path("manifest")
    @Produces(APPLICATION_XML)
    public Response getManifest(@Context final UriInfo uri) {
        // validate the session
        final Session session = this.getSession(uri);
        if (session == null || session.isExpired()) {
            return Response.status(Status.UNAUTHORIZED).build();
        }

        // retrieve course
        final String guid = session.getGuid();
        final Course course = this.courseManager.getCourse(this.getCourseQuery(uri).admin(guid).enrolled(guid)
                .loadManifest(true));

        // return the manifest
        if (course != null) {
            // parse the manifest
            final Document manifest = DomUtils.parse(course.getManifest());

            // set the course id and version in the manifest
            manifest.getDocumentElement().setAttribute("id", course.getId().toString());
            manifest.getDocumentElement().setAttribute("version", course.getVersion().toString());

            // return the manifest
            return Response.ok(DomUtils.asString(manifest)).build();
        }

        // return not found because a valid course wasn't found
        return Response.status(Status.NOT_FOUND).build();
    }

    @PUT
    @Path("manifest")
    public Response updateManifest(@Context final UriInfo uri, final InputStream in) {
        // validate the session
        final Session session = this.getSession(uri);
        if (session == null || session.isExpired()) {
            return Response.status(Status.UNAUTHORIZED).build();
        }

        // parse manifest
        final Document manifest = DomUtils.parse(in);
        if (manifest == null) {
            return Response.status(Status.BAD_REQUEST).build();
        }

        // attempt updating the manifest
        final Course course = this.support.updateManifest(this.getCourseQuery(uri).admin(session.getGuid()), manifest);

        // a valid course wasn't found to update
        if (course == null) {
            return Response.status(Status.UNAUTHORIZED).build();
        }

        return Response.ok().build();
    }

    @POST
    @Path("publish")
    public Response publish(@Context final UriInfo uri) {
        // validate the session
        final Session session = this.getSession(uri);
        if (session == null || session.isExpired()) {
            return Response.status(Status.UNAUTHORIZED).build();
        }

        // publish the specified course
        final Course course;
        try {
            course = this.courseManager.publishCourse(this.getCourseQuery(uri).admin(session.getGuid()));
        } catch (final CourseNotFoundException e) {
            return Response.status(Status.UNAUTHORIZED).build();
        } catch (final MultipleManifestExceptions e) {
            // return the errors
            return Response.status(Status.CONFLICT).entity(JaxbError.fromException(e)).build();
        } catch (final ManifestException e) {
            // return the error wrapped in an errors node
            final JaxbErrors errors = new JaxbErrors();
            errors.addError(e);
            return Response.status(Status.CONFLICT).entity(errors).build();
        }

        // generate a zip file for the published course
        // TODO: this should be moved to a background thread
        this.resourceManager.generateCourseZip(course);

        // TODO finish implementation
        return Response.ok().build();
    }

    @GET
    @Path("admins")
    public Response listAdmins(@Context final UriInfo uri) {
        // validate the session
        final Session session = this.getSession(uri);
        if (session == null || session.isExpired()) {
            return Response.status(Status.UNAUTHORIZED).build();
        }

        // retrieve the course
        final Course course = this.courseManager.getCourse(this.getCourseQuery(uri).admin(session.getGuid())
                .loadAdmins(true));
        if (course == null) {
            return Response.status(Status.UNAUTHORIZED).build();
        }

        // return success with the current list of admins
        final JaxbAdmins admins = new JaxbAdmins();
        admins.setUsers(course.getAdmins());
        return Response.ok(admins).build();
    }

    @POST
    @Path("admins")
    public Response updateAdmins(@Context final UriInfo uri, final MultivaluedMap<String, String> form) {
        // validate the session
        final Session session = this.getSession(uri);
        if (session == null || session.isExpired()) {
            return Response.status(Status.UNAUTHORIZED).build();
        }

        // generate toAdd and toRemove Sets
        final Set<String> toAdd = new HashSet<String>();
        final Set<String> toRemove = new HashSet<String>();
        toAdd.add(session.getGuid());
        Collection<String> guids;
        if ((guids = form.get("add")) != null) {
            toAdd.addAll(guids);
        }
        if ((guids = form.get("remove")) != null) {
            toRemove.addAll(guids);
        }

        // update the course admins
        final Course course;
        try {
            course = this.courseManager.updateCourseAdmins(this.getCourseQuery(uri).admin(session.getGuid()), toAdd,
                    toRemove);
        } catch (final CourseNotFoundException e) {
            return Response.status(Status.UNAUTHORIZED).build();
        }

        // return success with the current list of admins
        final JaxbAdmins admins = new JaxbAdmins();
        admins.setUsers(course.getAdmins());
        return Response.ok(admins).build();
    }

    @GET
    @Path("enrolled")
    public Response listEnrolled(@Context final UriInfo uri) {
        // validate the session
        final Session session = this.getSession(uri);
        if (session == null || session.isExpired()) {
            return Response.status(Status.UNAUTHORIZED).build();
        }

        // retrieve the course
        final Course course = this.courseManager.getCourse(this.getCourseQuery(uri).admin(session.getGuid())
                .loadEnrolled(true));
        if (course == null) {
            return Response.status(Status.UNAUTHORIZED).build();
        }

        // return success with the current list of enrolled
        final JaxbEnrolled enrolled = new JaxbEnrolled();
        enrolled.setUsers(course.getEnrolled());
        return Response.ok(enrolled).build();
    }

    @POST
    @Path("enrolled")
    public Response updateEnrolled(@Context final UriInfo uri, final MultivaluedMap<String, String> form) {
        // validate the session
        final Session session = this.getSession(uri);
        if (session == null || session.isExpired()) {
            return Response.status(Status.UNAUTHORIZED).build();
        }

        // generate toAdd and toRemove Sets
        final Set<String> toAdd = new HashSet<String>();
        final Set<String> toRemove = new HashSet<String>();
        Collection<String> guids;
        if ((guids = form.get("add")) != null) {
            toAdd.addAll(guids);
        }
        if ((guids = form.get("remove")) != null) {
            toRemove.addAll(guids);
        }

        // update the enrolled users
        final Course course;
        try {
            course = this.courseManager.updateCourseEnrolled(this.getCourseQuery(uri).admin(session.getGuid()), toAdd,
                    toRemove);
        } catch (final CourseNotFoundException e) {
            return Response.status(Status.UNAUTHORIZED).build();
        }

        // return success with the current list of enrolled users
        final JaxbEnrolled enrolled = new JaxbEnrolled();
        enrolled.setUsers(course.getEnrolled());
        return Response.ok(enrolled).build();
    }

    @GET
    @Path("zip")
    public Response getCourseZip(@Context final UriInfo uri) {
        // validate the session
        final Session session = this.getSession(uri);
        if (session == null || session.isExpired()) {
            return Response.status(Status.UNAUTHORIZED).build();
        }

        // find the course
        final String guid = session.getGuid();
        final Course course = this.courseManager.getCourse(this.getCourseQuery(uri).admin(guid).enrolled(guid));
        if (course == null) {
            return Response.status(Status.UNAUTHORIZED).build();
        }

        // return not found if the zip doesn't exist
        final String zipSha1 = course.getZipSha1();
        if (zipSha1 == null) {
            return Response.status(Status.NOT_FOUND).build();
        }

        // redirect to the actual location of the zip
        final Map<String, Object> values = this.getUriValues(uri);
        values.put(PARAM_RESOURCE_SHA1, zipSha1);
        return Response.temporaryRedirect(this.getResourceUriBuilder(uri).buildFromMap(values)).build();
    }
}