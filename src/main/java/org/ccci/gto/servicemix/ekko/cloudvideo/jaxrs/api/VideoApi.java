package org.ccci.gto.servicemix.ekko.cloudvideo.jaxrs.api;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;
import static org.ccci.gto.servicemix.common.jaxrs.api.Constants.PATH_API_KEY;
import static org.ccci.gto.servicemix.ekko.cloudvideo.jaxrs.api.Constants.PARAM_S3_BUCKET;
import static org.ccci.gto.servicemix.ekko.cloudvideo.jaxrs.api.Constants.PARAM_S3_KEY;
import static org.ccci.gto.servicemix.ekko.cloudvideo.jaxrs.api.Constants.PATH_VIDEO;

import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

import org.ccci.gto.servicemix.common.model.Client;
import org.ccci.gto.servicemix.ekko.cloudvideo.jaxb.model.JaxbVideo;
import org.ccci.gto.servicemix.ekko.cloudvideo.model.AwsFile;
import org.ccci.gto.servicemix.ekko.cloudvideo.model.Video;

@Path(PATH_API_KEY + "/videos/" + PATH_VIDEO)
public class VideoApi extends AbstractApi {
    @POST
    @Path("storeS3")
    @Produces({ APPLICATION_XML, APPLICATION_JSON })
    public Response uploadVideo(@Context final UriInfo uri, @FormParam(PARAM_S3_BUCKET) final String srcBucket,
            @FormParam(PARAM_S3_KEY) final String srcKey) {
        final Client client = this.getClient(uri);
        if (client == null) {
            return unauthorized(uri).build();
        }

        // short-circuit if this is an invalid video
        final Video video = this.manager.getVideo(this.getVideoQuery(client, uri));
        if (video == null) {
            return Response.status(Status.NOT_FOUND).build();
        }

        this.awsController.enqueueUpload(video, new AwsFile(srcBucket, srcKey), false);
        this.awsController.scheduleProcessUploads();

        return Response.ok(new JaxbVideo(video, this.awsController)).build();
    }

    @GET
    @Produces({APPLICATION_XML, APPLICATION_JSON})
    public Response getVideo(@Context final UriInfo uri) {
        final Client client = this.getClient(uri);
        if (client == null) {
            return unauthorized(uri).build();
        }

        final Video video = this.manager.getVideo(this.getVideoQuery(client, uri));
        if (video == null) {
            return Response.status(Status.NOT_FOUND).build();
        }

        return Response.ok(new JaxbVideo(video, this.awsController)).build();
    }
}
