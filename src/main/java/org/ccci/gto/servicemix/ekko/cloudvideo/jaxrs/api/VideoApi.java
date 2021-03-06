package org.ccci.gto.servicemix.ekko.cloudvideo.jaxrs.api;

import static javax.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;
import static org.ccci.gto.servicemix.common.jaxrs.api.Constants.PATH_OPTIONAL_API_KEY;
import static org.ccci.gto.servicemix.ekko.cloudvideo.jaxrs.api.Constants.PARAM_S3_BUCKET;
import static org.ccci.gto.servicemix.ekko.cloudvideo.jaxrs.api.Constants.PARAM_S3_DELETE_SOURCE;
import static org.ccci.gto.servicemix.ekko.cloudvideo.jaxrs.api.Constants.PARAM_S3_KEY;
import static org.ccci.gto.servicemix.ekko.cloudvideo.jaxrs.api.Constants.PATH_VIDEO;

import org.apache.cxf.jaxrs.ext.MessageContext;
import org.ccci.gto.servicemix.common.model.Client;
import org.ccci.gto.servicemix.ekko.cloudvideo.jaxb.model.JaxbVideo;
import org.ccci.gto.servicemix.ekko.cloudvideo.model.AwsFile;
import org.ccci.gto.servicemix.ekko.cloudvideo.model.Video;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@Path(PATH_OPTIONAL_API_KEY + "videos/" + PATH_VIDEO)
public class VideoApi extends AbstractApi {
    @POST
    @Path("storeS3")
    @Produces({ APPLICATION_XML, APPLICATION_JSON })
    public Response uploadVideo(@Context final MessageContext cxt, @Context final UriInfo uri,
                                @FormParam(PARAM_S3_BUCKET) final String srcBucket,
                                @FormParam(PARAM_S3_KEY) final String srcKey, @FormParam(PARAM_S3_DELETE_SOURCE)
    final boolean deleteSource) {
        final Client client = this.getClient(cxt, uri);
        if (client == null) {
            return unauthorized().build();
        }

        // short-circuit if this is an invalid video
        final Video video = this.manager.getVideo(this.getVideoQuery(client, uri).deleted(false));
        if (video == null) {
            return Response.status(Status.NOT_FOUND).build();
        }

        this.videoStateMachine.enqueueUpload(video, new AwsFile(srcBucket, srcKey), deleteSource);

        return Response.ok(new JaxbVideo(video, this.awsController)).build();
    }

    @GET
    @Produces({APPLICATION_XML, APPLICATION_JSON})
    public Response getVideo(@Context final MessageContext cxt, @Context final UriInfo uri) {
        final Client client = this.getClient(cxt, uri);
        if (client == null) {
            return unauthorized().build();
        }

        final Video video = this.manager.getVideo(this.getVideoQuery(client, uri));
        if (video == null) {
            return Response.status(Status.NOT_FOUND).build();
        }

        return Response.ok(new JaxbVideo(video, this.awsController)).build();
    }

    @DELETE
    public Response deleteVideo(@Context final MessageContext cxt, @Context final UriInfo uri) {
        final Client client = this.getClient(cxt, uri);
        if (client == null) {
            return unauthorized().build();
        }

        final Video video = this.manager.getVideo(this.getVideoQuery(client, uri));
        if (video == null) {
            return Response.status(Status.NOT_FOUND).build();
        }

        // try deleting this video
        final boolean deleted = this.videoStateMachine.deleteVideo(video);

        // throw a conflict error if we were unable to delete the video
        if (!deleted) {
            return Response.status(Status.CONFLICT).build();
        }

        // return success
        return Response.ok().build();
    }

    @POST
    @Path("courses")
    @Consumes(APPLICATION_FORM_URLENCODED)
    public Response updateCourses(@Context final MessageContext cxt, @Context final UriInfo uri,
                                  final MultivaluedMap<String, String> form) {
        final Client client = this.getClient(cxt, uri);
        if (client == null) {
            return unauthorized().build();
        }

        // generate toAdd and toRemove Sets
        final Set<Long> toAdd = new HashSet<>();
        final Set<Long> toRemove = new HashSet<>();
        Collection<String> ids;
        if ((ids = form.get("add")) != null) {
            for (final String id : ids) {
                try {
                    toAdd.add(Long.parseLong(id));
                } catch (final Exception e) {
                    return Response.status(Status.BAD_REQUEST).build();
                }
            }
        }
        if ((ids = form.get("remove")) != null) {
            for (final String id : ids) {
                try {
                    toRemove.add(Long.parseLong(id));
                } catch (final Exception e) {
                    return Response.status(Status.BAD_REQUEST).build();
                }
            }
        }

        // get the video that we are updating
        final Video video = manager.getVideo(getVideoQuery(client, uri).deleted(false));
        if (video == null) {
            return Response.status(Status.NOT_FOUND).build();
        }

        // update mapped courses
        this.manager.updateCourses(video, toAdd, toRemove);

        // TODO: return a list of mapped courses??
        return Response.ok().build();
    }
}
