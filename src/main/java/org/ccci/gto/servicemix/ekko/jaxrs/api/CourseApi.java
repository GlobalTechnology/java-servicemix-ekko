package org.ccci.gto.servicemix.ekko.jaxrs.api;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;
import static org.ccci.gto.servicemix.common.jaxrs.api.Constants.PATH_OPTIONAL_SESSION;
import static org.ccci.gto.servicemix.ekko.jaxrs.api.Constants.PARAM_ENROLLMENT_TYPE;
import static org.ccci.gto.servicemix.ekko.jaxrs.api.Constants.PARAM_PUBLIC;
import static org.ccci.gto.servicemix.ekko.jaxrs.api.Constants.PATH_COURSE;

import org.apache.cxf.jaxrs.ext.MessageContext;
import org.ccci.gto.persistence.tx.TransactionService;
import org.ccci.gto.servicemix.common.model.Session;
import org.ccci.gto.servicemix.common.util.ResponseUtils;
import org.ccci.gto.servicemix.ekko.CourseNotFoundException;
import org.ccci.gto.servicemix.ekko.DomUtils;
import org.ccci.gto.servicemix.ekko.ManifestException;
import org.ccci.gto.servicemix.ekko.MultipleManifestExceptions;
import org.ccci.gto.servicemix.ekko.ResourceManager;
import org.ccci.gto.servicemix.ekko.jaxb.model.JaxbCourse;
import org.ccci.gto.servicemix.ekko.jaxb.model.JaxbError;
import org.ccci.gto.servicemix.ekko.jaxb.model.JaxbErrors;
import org.ccci.gto.servicemix.ekko.jaxb.model.JaxbSettings;
import org.ccci.gto.servicemix.ekko.jaxb.model.JaxbUsers;
import org.ccci.gto.servicemix.ekko.model.Course;
import org.ccci.gto.servicemix.ekko.model.Course.CourseQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.w3c.dom.Document;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
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
import java.io.InputStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;

@Path(PATH_OPTIONAL_SESSION + "courses/" + PATH_COURSE)
public class CourseApi extends AbstractApi {
    /**
     * API Support functions used for loading data
     */
    public interface Support {
        Course updateManifest(CourseQuery courseQuery, Document manifest);
    }

    // @Autowired
    private Support support;

    @Autowired
    private ResourceManager resourceManager;

    @Autowired
    private TransactionService txService;

    public void setSupport(final Support support) {
        this.support = support;
    }

    public final void setResourceManager(final ResourceManager resourceManager) {
        this.resourceManager = resourceManager;
    }

    public final void setTransactionService(final TransactionService transactionService) {
        this.txService = transactionService;
    }

    /**
     * Retrieves the course meta-data for a specified course
     *
     * @param uri
     * @return
     */
    @GET
    @Produces(APPLICATION_XML)
    public Response getCourse(@Context final MessageContext cxt, @Context final UriInfo uri) {
        // validate the session
        final Session session = this.getSession(cxt, uri);
        if (session == null || session.isExpired()) {
            return this.unauthorized(cxt, uri).build();
        }

        // retrieve course
        final String guid = session.getGuid();
        final Course course = this.courseManager.getCourse(this.getCourseQuery(uri).visibleTo(guid).loadManifest()
                .loadPermissionAttrs());
        if (course != null) {
            return Response.ok(new JaxbCourse(course, true, guid)).build();
        }

        // return not found because a valid course wasn't found
        return Response.status(Status.NOT_FOUND).build();
    }

    @GET
    @Path("settings")
    @Produces({APPLICATION_XML, APPLICATION_JSON})
    public Response getSettings(@Context final MessageContext cxt, @Context final UriInfo uri) {
        // validate the session
        final Session session = this.getSession(cxt, uri);
        if (session == null || session.isExpired() || session.isGuest()) {
            return this.unauthorized(cxt, uri).build();
        }

        final Course course = this.courseManager.getCourse(this.getCourseQuery(uri).admin(session.getGuid()));
        if (course == null) {
            return ResponseUtils.unauthorized().build();
        }

        return Response.ok(new JaxbSettings(course)).build();
    }

    @POST
    @Path("settings")
    @Produces({APPLICATION_XML, APPLICATION_JSON})
    public Response updateSettings(@Context final MessageContext cxt, @Context final UriInfo uri,
                                   final MultivaluedMap<String, String> form) {
        // validate the session
        final Session session = this.getSession(cxt, uri);
        if (session == null || session.isExpired() || session.isGuest()) {
            return this.unauthorized(cxt, uri).build();
        }

        try {
            return this.txService.inTransaction(new Callable<Response>() {
                @Override
                public Response call() {
                    // make sure the current user is an admin
                    final Course course = courseManager.getCourse(getCourseQuery(uri).admin(session.getGuid()));
                    if (course == null) {
                        return ResponseUtils.unauthorized().build();
                    }

                    // update settings based on what was submitted
                    if (form.containsKey(PARAM_PUBLIC)) {
                        course.setPublic(Boolean.parseBoolean(form.getFirst(PARAM_PUBLIC)));
                    }
                    if (form.containsKey(PARAM_ENROLLMENT_TYPE)) {
                        final String type = form.getFirst(PARAM_ENROLLMENT_TYPE);
                        if (!"disabled".equalsIgnoreCase(type)) {
                            course.setEnrollment(type);
                        }
                    }

                    // return success
                    return Response.ok(new JaxbSettings(course)).build();
                }
            });
        } catch (final Exception e) {
            // we encountered some sort of exception
            return Response.status(Status.BAD_REQUEST).build();
        }
    }

    @POST
    @Path("enroll")
    @Produces(APPLICATION_XML)
    public Response enroll(@Context final MessageContext cxt, @Context final UriInfo uri) {
        // validate the session
        final Session session = this.getSession(cxt, uri);
        if (session == null || session.isExpired() || session.isGuest()) {
            return this.unauthorized(cxt, uri).build();
        }

        // generate the CourseQuery
        final String guid = session.getGuid();
        final CourseQuery query = this.getCourseQuery(uri).visibleTo(guid).loadPermissionAttrs().loadManifest();

        // enroll the user in this course
        final Course course;
        try {
            course = this.courseManager.enroll(query, guid);
        } catch (final CourseNotFoundException e) {
            return ResponseUtils.unauthorized().build();
        }

        // return the updated Course object to save an additional round trip for
        // the updated state
        return Response.ok(new JaxbCourse(course, true, guid)).build();
    }

    @POST
    @Path("unenroll")
    @Produces(APPLICATION_XML)
    public Response unenroll(@Context final MessageContext cxt, @Context final UriInfo uri) {
        // validate the session
        final Session session = this.getSession(cxt, uri);
        if (session == null || session.isExpired() || session.isGuest()) {
            return this.unauthorized(cxt, uri).build();
        }

        // generate the CourseQuery
        final String guid = session.getGuid();
        final CourseQuery query = this.getCourseQuery(uri).enrolled(guid).pending(guid);

        // remove the current user from the course
        try {
            this.courseManager.unenroll(query, guid);
        } catch (final CourseNotFoundException e) {
            return ResponseUtils.unauthorized().build();
        }

        // return success response
        // return the course xml if we can still see it
        final Response response;
        if ((response = this.getCourse(cxt, uri)) != null && response.getStatus() == 200) {
            return response;
        }
        // otherwise return a simple OK response
        else {
            return Response.ok().build();
        }
    }

    @DELETE
    public Response deleteCourse(@Context final MessageContext cxt, @Context final UriInfo uri) {
        // validate the session
        final Session session = this.getSession(cxt, uri);
        if (session == null || session.isExpired() || session.isGuest()) {
            return this.unauthorized(cxt, uri).build();
        }

        // generate the CourseQuery
        final CourseQuery query = this.getCourseQuery(uri).admin(session.getGuid());

        // unpublish the course
        final Course course;
        try {
            course = this.courseManager.unpublishCourse(query);
        } catch (final CourseNotFoundException e) {
            return ResponseUtils.unauthorized().build();
        }

        // remove all unpublished resources
        this.resourceManager.removeUnpublishedResources(course);

        // attempt to delete this course
        try {
            this.courseManager.deleteCourse(query);
        } catch (final CourseNotFoundException e) {
            return ResponseUtils.unauthorized().build();
        }

        return Response.ok().build();
    }

    @GET
    @Path("manifest")
    @Produces(APPLICATION_XML)
    public Response getManifest(@Context final MessageContext cxt, @Context final UriInfo uri) {
        // validate the session
        final Session session = this.getSession(cxt, uri);
        if (session == null || session.isExpired()) {
            return this.unauthorized(cxt, uri).build();
        }

        // retrieve course
        final String guid = session.getGuid();
        final Course course = this.courseManager.getCourse(this.getCourseQuery(uri).contentVisibleTo(guid)
                .loadManifest());

        // return the manifest
        if (course != null) {
            // parse the manifest
            final Document manifest = DomUtils.parse(course.getManifest());

            // set the course id and version in the manifest
            manifest.getDocumentElement().setAttribute("id", Long.toString(course.getId()));
            manifest.getDocumentElement().setAttribute("version", Long.toString(course.getVersion()));

            // return the manifest
            return Response.ok(DomUtils.asString(manifest)).build();
        }

        // return not found because a valid course wasn't found
        return Response.status(Status.NOT_FOUND).build();
    }

    @PUT
    @Path("manifest")
    @Consumes(APPLICATION_XML)
    public Response updateManifest(@Context final MessageContext cxt, @Context final UriInfo uri,
                                   final InputStream in) {
        // validate the session
        final Session session = this.getSession(cxt, uri);
        if (session == null || session.isExpired() || session.isGuest()) {
            return this.unauthorized(cxt, uri).build();
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
            return ResponseUtils.unauthorized().build();
        }

        return Response.ok().build();
    }

    @POST
    @Path("publish")
    public Response publish(@Context final MessageContext cxt, @Context final UriInfo uri) {
        // validate the session
        final Session session = this.getSession(cxt, uri);
        if (session == null || session.isExpired() || session.isGuest()) {
            return this.unauthorized(cxt, uri).build();
        }

        // publish the specified course
        try {
            this.courseManager.publishCourse(this.getCourseQuery(uri).admin(session.getGuid()));
        } catch (final CourseNotFoundException e) {
            return ResponseUtils.unauthorized().build();
        } catch (final MultipleManifestExceptions e) {
            // return the errors
            return Response.status(Status.CONFLICT).entity(JaxbError.fromException(e)).build();
        } catch (final ManifestException e) {
            // return the error wrapped in an errors node
            final JaxbErrors errors = new JaxbErrors();
            errors.addError(e);
            return Response.status(Status.CONFLICT).entity(errors).build();
        }

        // TODO finish implementation
        return Response.ok().build();
    }

    @GET
    @Path("admins")
    @Produces(APPLICATION_XML)
    public Response listAdmins(@Context final MessageContext cxt, @Context final UriInfo uri) {
        // validate the session
        final Session session = this.getSession(cxt, uri);
        if (session == null || session.isExpired() || session.isGuest()) {
            return this.unauthorized(cxt, uri).build();
        }

        // retrieve the course
        final Course course = this.courseManager.getCourse(this.getCourseQuery(uri).admin(session.getGuid())
                .loadAdmins());
        if (course == null) {
            return ResponseUtils.unauthorized().build();
        }

        // return success with the current list of admins
        final JaxbUsers users = new JaxbUsers();
        users.setUsers(course.getAdmins());
        return Response.ok(users).build();
    }

    @POST
    @Path("admins")
    @Produces(APPLICATION_XML)
    public Response updateAdmins(@Context final MessageContext cxt, @Context final UriInfo uri,
                                 final MultivaluedMap<String, String> form) {
        // validate the session
        final Session session = this.getSession(cxt, uri);
        if (session == null || session.isExpired() || session.isGuest()) {
            return this.unauthorized(cxt, uri).build();
        }

        // generate toAdd and toRemove Sets
        final Set<String> toAdd = new HashSet<>();
        final Set<String> toRemove = new HashSet<>();
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
            return ResponseUtils.unauthorized().build();
        }

        // return success with the current list of admins
        final JaxbUsers users = new JaxbUsers();
        users.setUsers(course.getAdmins());
        return Response.ok(users).build();
    }

    @GET
    @Path("enrolled")
    @Produces(APPLICATION_XML)
    public Response listEnrolled(@Context final MessageContext cxt, @Context final UriInfo uri) {
        // validate the session
        final Session session = this.getSession(cxt, uri);
        if (session == null || session.isExpired() || session.isGuest()) {
            return this.unauthorized(cxt, uri).build();
        }

        // retrieve the course
        final Course course = this.courseManager.getCourse(this.getCourseQuery(uri).admin(session.getGuid())
                .loadEnrolled());
        if (course == null) {
            return ResponseUtils.unauthorized().build();
        }

        // return success with the current list of enrolled
        final JaxbUsers users = new JaxbUsers();
        users.setUsers(course.getEnrolled());
        return Response.ok(users).build();
    }

    @POST
    @Path("enrolled")
    @Produces(APPLICATION_XML)
    public Response updateEnrolled(@Context final MessageContext cxt, @Context final UriInfo uri,
                                   final MultivaluedMap<String, String> form) {
        // validate the session
        final Session session = this.getSession(cxt, uri);
        if (session == null || session.isExpired() || session.isGuest()) {
            return this.unauthorized(cxt, uri).build();
        }

        // generate toAdd and toRemove Sets
        final Set<String> toAdd = new HashSet<>();
        final Set<String> toRemove = new HashSet<>();
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
            course = this.courseManager.updateCourseEnrolled(this.getCourseQuery(uri).admin(session.getGuid()),
                    toAdd, toRemove);
        } catch (final CourseNotFoundException e) {
            return ResponseUtils.unauthorized().build();
        }

        // return success with the current list of enrolled users
        final JaxbUsers users = new JaxbUsers();
        users.setUsers(course.getEnrolled());
        return Response.ok(users).build();
    }

    @GET
    @Path("pending")
    @Produces(APPLICATION_XML)
    public Response listPending(@Context final MessageContext cxt, @Context final UriInfo uri) {
        // validate the session
        final Session session = this.getSession(cxt, uri);
        if (session == null || session.isExpired() || session.isGuest()) {
            return this.unauthorized(cxt, uri).build();
        }

        // retrieve the course
        final Course course = this.courseManager.getCourse(this.getCourseQuery(uri).admin(session.getGuid())
                .loadPending());
        if (course == null) {
            return ResponseUtils.unauthorized().build();
        }

        // return success with the current list of pending enrollments
        final JaxbUsers users = new JaxbUsers();
        users.setUsers(course.getPending());
        return Response.ok(users).build();
    }

    @POST
    @Path("pending")
    @Produces(APPLICATION_XML)
    public Response updatePending(@Context final MessageContext cxt, @Context final UriInfo uri,
                                  final MultivaluedMap<String, String> form) {
        // validate the session
        final Session session = this.getSession(cxt, uri);
        if (session == null || session.isExpired() || session.isGuest()) {
            return this.unauthorized(cxt, uri).build();
        }

        // generate toAdd and toRemove Sets
        final Set<String> toAdd = new HashSet<>();
        final Set<String> toRemove = new HashSet<>();
        Collection<String> guids;
        if ((guids = form.get("add")) != null) {
            toAdd.addAll(guids);
        }
        if ((guids = form.get("remove")) != null) {
            toRemove.addAll(guids);
        }

        // update the pending users
        final Course course;
        try {
            course = this.courseManager.updateCoursePending(this.getCourseQuery(uri).admin(session.getGuid()), toAdd,
                    toRemove);
        } catch (final CourseNotFoundException e) {
            return ResponseUtils.unauthorized().build();
        }

        // return success with the current list of pending enrollments
        final JaxbUsers users = new JaxbUsers();
        users.setUsers(course.getPending());
        return Response.ok(users).build();
    }
}
