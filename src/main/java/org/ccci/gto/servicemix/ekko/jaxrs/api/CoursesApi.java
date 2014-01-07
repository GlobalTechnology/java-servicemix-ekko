package org.ccci.gto.servicemix.ekko.jaxrs.api;

import static javax.ws.rs.core.MediaType.APPLICATION_XML;
import static org.ccci.gto.servicemix.common.jaxrs.api.Constants.PATH_SESSION;
import static org.ccci.gto.servicemix.ekko.jaxrs.api.Constants.PARAM_COURSE;
import static org.ccci.gto.servicemix.ekko.jaxrs.api.Constants.PARAM_LIMIT;
import static org.ccci.gto.servicemix.ekko.jaxrs.api.Constants.PARAM_START;

import java.io.InputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import org.apache.cxf.jaxrs.ext.MessageContext;
import org.ccci.gto.servicemix.common.model.Session;
import org.ccci.gto.servicemix.ekko.DomUtils;
import org.ccci.gto.servicemix.ekko.jaxb.model.JaxbCourse;
import org.ccci.gto.servicemix.ekko.jaxb.model.JaxbCourses;
import org.ccci.gto.servicemix.ekko.model.Course;
import org.ccci.gto.servicemix.ekko.model.Course.CourseQuery;
import org.w3c.dom.Document;

@Path(PATH_SESSION + "/courses")
public class CoursesApi extends AbstractApi {
    @POST
    @Consumes(APPLICATION_XML)
    @Produces(APPLICATION_XML)
    public Response createCourse(@Context final MessageContext cxt, @Context final UriInfo uri, final InputStream in) {
        // validate the session
        final Session session = this.getSession(uri);
        if (session == null || session.isExpired() || session.isGuest()) {
            return this.unauthorized(cxt, uri).build();
        }

        // parse manifest
        final Document manifest = DomUtils.parse(in);
        if (manifest == null) {
            return Response.status(Status.BAD_REQUEST).build();
        }

        // create new course object
        final String guid = session.getGuid();
        final Course newCourse = new Course();
        newCourse.setPendingManifest(DomUtils.asString(manifest));
        newCourse.addAdmin(guid);
        final Course course = this.courseManager.createCourse(newCourse);

        // return success
        final Map<String, Object> values = new HashMap<String, Object>();
        values.putAll(this.getUriValues(uri));
        values.put(PARAM_COURSE, course.getId());
        final URI courseUri = this.getCourseUriBuilder(cxt, uri).buildFromMap(values);
        return Response.created(courseUri).entity(new JaxbCourse(course, false, null)).build();
    }

    /**
     * Return the courses a user is authorized to see
     * 
     * @param uri
     * @return
     */
    @GET
    @Produces(APPLICATION_XML)
    public Response getCourses(@Context final MessageContext cxt, @Context final UriInfo uri,
            @QueryParam(PARAM_START) @DefaultValue("0") int start,
            @QueryParam(PARAM_LIMIT) @DefaultValue("10") int limit) {
        // validate the session
        final Session session = this.getSession(uri);
        if (session == null || session.isExpired()) {
            return this.unauthorized(cxt, uri).build();
        }

        // sanitize start and limit
        if (start < 0) {
            start = 0;
        }
        if (limit < 0) {
            limit = 10;
        } else if (limit > 50) {
            limit = 10;
        }

        // fetch the courses, we fetch 1 more than the limit to determine if
        // additional requests are needed
        final String guid = session.getGuid();
        final List<Course> courses = this.courseManager.getCourses(new CourseQuery().visibleTo(guid).loadManifest()
                .loadPermissionAttrs().start(start).limit(limit + 1));
        final int size = courses.size();

        // generate response objects
        final JaxbCourses jaxbCourses = new JaxbCourses();
        jaxbCourses.setStart(start);
        jaxbCourses.setLimit(limit);
        if (size > limit) {
            final UriBuilder moreUri = this.getRequestUriBuilder(cxt, uri);
            moreUri.replaceQueryParam(PARAM_START, start + limit).replaceQueryParam(PARAM_LIMIT, limit);
            jaxbCourses.setMoreUri(moreUri.build());
        }
        for (final Course course : courses.subList(0, size < limit ? size : limit)) {
            jaxbCourses.addCourse(new JaxbCourse(course, true, guid));
        }

        // return the courses
        return Response.ok(jaxbCourses).build();
    }
}
