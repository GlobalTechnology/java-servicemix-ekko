package org.ccci.gto.servicemix.ekko.jaxrs.api;

import static javax.ws.rs.core.MediaType.APPLICATION_XML;
import static org.ccci.gto.servicemix.common.jaxrs.api.Constants.PATH_SESSION;
import static org.ccci.gto.servicemix.ekko.jaxrs.api.Constants.PARAM_RESOURCE_SHA1;
import static org.ccci.gto.servicemix.ekko.jaxrs.api.Constants.PATH_COURSE;
import static org.ccci.gto.servicemix.ekko.jaxrs.api.Constants.PATH_RESOURCE_SHA1;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.Callable;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import org.ccci.gto.persistence.tx.TransactionService;
import org.ccci.gto.servicemix.common.model.Session;
import org.ccci.gto.servicemix.common.util.ResponseUtils;
import org.ccci.gto.servicemix.ekko.ResourceAlreadyExistsException;
import org.ccci.gto.servicemix.ekko.ResourceException;
import org.ccci.gto.servicemix.ekko.ResourceManager;
import org.ccci.gto.servicemix.ekko.ResourceNotFoundException;
import org.ccci.gto.servicemix.ekko.jaxb.model.JaxbResource;
import org.ccci.gto.servicemix.ekko.jaxb.model.JaxbResources;
import org.ccci.gto.servicemix.ekko.model.Course;
import org.ccci.gto.servicemix.ekko.model.Resource;
import org.ccci.gto.servicemix.ekko.model.ResourcePrimaryKey;
import org.springframework.beans.factory.annotation.Autowired;

@Path(PATH_SESSION + "/courses/" + PATH_COURSE + "/resources")
public class ResourcesApi extends AbstractApi {
    @Autowired
    private ResourceManager resourceManager;

    @Autowired
    private TransactionService txService;

    public void setResourceManager(final ResourceManager resourceManager) {
        this.resourceManager = resourceManager;
    }

    public final void setTransactionService(final TransactionService transactionService) {
        this.txService = transactionService;
    }

    @POST
    public Response storeResource(@Context final UriInfo uri, @HeaderParam("Content-Type") final String mimeType,
            final InputStream in) {
        // validate the session
        final Session session = this.getSession(uri);
        if (session == null || session.isExpired() || session.isGuest()) {
            return this.invalidSession(uri).build();
        }

        // validate the specified course
        final Course course = this.courseManager.getCourse(this.getCourseQuery(uri).admin(session.getGuid()));
        if (course == null) {
            return ResponseUtils.unauthorized().build();
        }

        // store the uploaded file
        final Resource resource;
        try {
            // TODO use the Content-Type header
            resource = this.resourceManager.storeResource(course, mimeType, in);
        } catch (final ResourceAlreadyExistsException e) {
            final ResponseBuilder response = Response.status(Status.FORBIDDEN).entity("Resource already exists");

            // attach the location of the resource to the response
            final ResourcePrimaryKey key = e.getResourceKey();
            if (key != null) {
                response.contentLocation(this.getRequestUriBuilder(uri).path(key.getSha1()).replaceQuery(null).build());
            }

            return response.build();
        }

        // return a created response for the new file
        if (resource != null) {
            final JaxbResource jaxbResource = new JaxbResource(resource, this.getResourceUriBuilder(uri),
                    this.getUriValues(uri));
            return Response.created(jaxbResource.getUri()).entity(jaxbResource).build();
        }

        // There was an error storing the file
        return Response.status(Status.SERVICE_UNAVAILABLE).build();
    }

    @GET
    @Produces(APPLICATION_XML)
    public Response getResources(@Context final UriInfo uri) {
        // validate the session
        final Session session = this.getSession(uri);
        if (session == null || session.isExpired() || session.isGuest()) {
            return this.invalidSession(uri).build();
        }

        // load the course
        final Course course = this.courseManager.getCourse(this.getCourseQuery(uri).admin(session.getGuid())
                .loadResources(true));
        if (course == null) {
            return ResponseUtils.unauthorized().build();
        }

        // generate JAXB objects
        final UriBuilder resourceUri = this.getResourceUriBuilder(uri);
        final Map<String, Object> values = this.getUriValues(uri);
        final JaxbResources jaxbResources = new JaxbResources();
        for (final Resource resource : course.getResources()) {
            jaxbResources.addResource(new JaxbResource(resource, resourceUri, values));
        }

        // return the resources
        return Response.ok(jaxbResources).build();
    }

    @GET
    @Path(PATH_RESOURCE_SHA1)
    public Response getResourceBySha1(@Context final UriInfo uri) {
        // validate the session
        final Session session = this.getSession(uri);
        if (session == null || session.isExpired()) {
            return this.invalidSession(uri).build();
        }

        // retrieve the requested resource, checking authorization in the
        // process
        final String guid = session.getGuid();
        final Resource resource;
        try {
            resource = txService.inTransaction(new Callable<Resource>() {
                @Override
                public Resource call() throws Exception {
                    final Course course;
                    final Resource resource;
                    if ((course = courseManager.getCourse(getCourseId(uri))) != null && course.isVisibleTo(guid)
                            && (resource = course.getResource(getResourceSha1(uri))) != null
                            && resource.isVisibleTo(guid)) {
                        return resource;
                    }

                    return null;
                }
            });
        } catch (final Exception e) {
            return ResponseUtils.unauthorized().build();
        }

        // return a 404 if a resource wasn't found
        if (resource == null) {
            return ResponseUtils.unauthorized().build();
        }

        // get an InputStream for the requested file
        final InputStream file;
        try {
            file = this.resourceManager.loadResource(resource);
        } catch (final ResourceNotFoundException e) {
            return Response.status(Status.NOT_FOUND).build();
        } catch (final ResourceException e) {
            return Response.status(Status.SERVICE_UNAVAILABLE).build();
        }

        // return a 404 because we didn't get a valid InputStream
        if (file == null) {
            return Response.status(Status.NOT_FOUND).build();
        }

        // return the resource
        final String mimeType = resource.getMimeType();
        final ResponseBuilder response = Response.ok(file).header("Content-Length", resource.getSize());
        if (mimeType != null) {
            response.header("Content-Type", mimeType);
        }
        return response.build();
    }

    @DELETE
    @Path(PATH_RESOURCE_SHA1)
    public Response deleteResourceBySha1(@Context final UriInfo uri) {
        // validate the session
        final Session session = this.getSession(uri);
        if (session == null || session.isExpired() || session.isGuest()) {
            return this.invalidSession(uri).build();
        }

        // validate the specified course
        final Course course = this.courseManager.getCourse(this.getCourseQuery(uri).admin(session.getGuid())
                .loadResources(true));
        if (course == null) {
            return ResponseUtils.unauthorized().build();
        }

        // throw a 404 if the resource doesn't exist
        final Resource resource = course.getResource(this.getResourceSha1(uri));
        if (resource == null) {
            return Response.status(Status.NOT_FOUND).build();
        }

        // don't delete published resources
        if (resource.isPublished()) {
            return Response.status(Status.FORBIDDEN).build();
        }

        // delete the resource
        this.resourceManager.removeResource(resource);

        // return success
        return Response.ok().build();
    }

    private String getResourceSha1(final UriInfo uri) {
        return uri.getPathParameters().getFirst(PARAM_RESOURCE_SHA1);
    }
}
