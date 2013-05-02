package org.ccci.gto.servicemix.ekko.jaxrs.api;

import static javax.ws.rs.core.MediaType.APPLICATION_XML;
import static org.ccci.gto.servicemix.cas.jaxrs.api.Constants.PATH_SESSION;
import static org.ccci.gto.servicemix.ekko.jaxrs.api.Constants.PARAM_COURSE;
import static org.ccci.gto.servicemix.ekko.jaxrs.api.Constants.PATH_COURSE;

import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

import org.ccci.gto.servicemix.cas.model.Session;
import org.ccci.gto.servicemix.ekko.DomUtils;
import org.ccci.gto.servicemix.ekko.jaxb.model.JaxbCourse;
import org.ccci.gto.servicemix.ekko.jaxb.model.JaxbCourses;
import org.ccci.gto.servicemix.ekko.model.Course;
import org.springframework.beans.factory.annotation.Autowired;
import org.w3c.dom.Document;

@Path(PATH_SESSION + "/course")
public class CoursesApi extends AbstractApi {
    @Autowired(required = false)
    private Support support;

    public void setSupport(final Support support) {
        this.support = support;
    }

    /**
     * Return all the courses a user is authorized to see
     * 
     * @param uri
     * @return
     */
    @GET
    @Produces(APPLICATION_XML)
    public Response getCourses(@Context final UriInfo uri) {
        // validate the session
        final Session session = this.getSession(uri);
        if (session == null || session.isExpired()) {
            return Response.status(Status.UNAUTHORIZED).build();
        }

        // load the courses
        final JaxbCourses courses = new JaxbCourses();
        for (final Course course : this.support.getCourses(session.getGuid())) {
            courses.addCourse(new JaxbCourse(course));
        }

        // return the courses
        return Response.ok(courses).build();
    }

    /**
     * Retrieves the course meta-data for a specified course
     * 
     * @param uri
     * @return
     */
    @GET
    @Path(PATH_COURSE)
    @Produces(APPLICATION_XML)
    public Response getCourse(@Context final UriInfo uri) {
        // validate the session
        final Session session = this.getSession(uri);
        if (session == null || session.isExpired()) {
            return Response.status(Status.UNAUTHORIZED).build();
        }

        // retrieve course
        final Course course = this.support.getCourse(this.getCourseId(uri));
        if (course != null) {
            return Response.ok(new JaxbCourse(course)).build();
        }

        // return not found because a valid course wasn't found
        return Response.status(Status.NOT_FOUND).build();
    }

    @GET
    @Path(PATH_COURSE + "/manifest")
    @Produces(APPLICATION_XML)
    public Response getCourseManifest(@Context final UriInfo uri) {
        // validate the session
        final Session session = this.getSession(uri);
        if (session == null || session.isExpired()) {
            return Response.status(Status.UNAUTHORIZED).build();
        }

        // retrieve course
        final Course course = this.support.getCourse(this.getCourseId(uri));

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

    private Long getCourseId(final UriInfo uri) {
        return Long.valueOf(uri.getPathParameters().getFirst(PARAM_COURSE));
    }

    public interface Support {
        List<Course> getCourses(String guid);

        Course getCourse(Long id);
    }
}
