package org.ccci.gto.servicemix.ekko.cloudvideo.jaxrs.api;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;
import static org.ccci.gto.servicemix.common.jaxrs.api.Constants.PATH_API_KEY;
import static org.ccci.gto.servicemix.ekko.cloudvideo.jaxrs.api.Constants.PARAM_LIMIT;
import static org.ccci.gto.servicemix.ekko.cloudvideo.jaxrs.api.Constants.PARAM_START;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.ccci.gto.servicemix.common.jaxrs.api.ClientAwareApi;
import org.ccci.gto.servicemix.common.model.Client;
import org.ccci.gto.servicemix.ekko.cloudvideo.VideoManager;
import org.ccci.gto.servicemix.ekko.cloudvideo.jaxb.model.JaxbVideo;
import org.ccci.gto.servicemix.ekko.cloudvideo.jaxb.model.JaxbVideos;
import org.ccci.gto.servicemix.ekko.cloudvideo.jaxb.model.JaxbVideosJson;
import org.ccci.gto.servicemix.ekko.cloudvideo.jaxb.model.JaxbVideosXml;
import org.ccci.gto.servicemix.ekko.cloudvideo.model.Video;
import org.ccci.gto.servicemix.ekko.cloudvideo.model.Video.VideoQuery;
import org.springframework.beans.factory.annotation.Autowired;

@Path(PATH_API_KEY + "/videos")
public class VideosApi extends ClientAwareApi {
    @Autowired
    private VideoManager manager;

    @POST
    @Produces({ APPLICATION_XML, APPLICATION_JSON })
    public Response createVideo(@Context final UriInfo uri) {
        final Client client = this.getClient(uri);
        if (client == null) {
            return unauthorized(uri).build();
        }

        // create a new video object
        final Video video = this.manager.createVideo(new Video(client));

        // return the new video object
        return Response.ok(new JaxbVideo(video)).build();
    }

    @GET
    @Produces({ APPLICATION_JSON })
    public Response getVideosJson(@Context UriInfo uri, @QueryParam(PARAM_START) @DefaultValue("0") int start,
            @QueryParam(PARAM_LIMIT) @DefaultValue("40") int limit) {
        return this.getVideos(uri, start, limit, true);
    }

    @GET
    @Produces({ APPLICATION_XML })
    public Response getVideosXml(@Context UriInfo uri, @QueryParam(PARAM_START) @DefaultValue("0") int start,
            @QueryParam(PARAM_LIMIT) @DefaultValue("40") int limit) {
        return this.getVideos(uri, start, limit, false);
    }

    private Response getVideos(@Context final UriInfo uri, int start, int limit, final boolean json) {
        final Client client = this.getClient(uri);
        if (client == null) {
            return unauthorized(uri).build();
        }

        // sanitize start and limit
        if (start < 0) {
            start = 0;
        }
        if (limit < 0) {
            limit = 40;
        } else if (limit > 80) {
            limit = 40;
        }

        // populate output videos list
        final JaxbVideos videos = json ? new JaxbVideosJson() : new JaxbVideosXml();
        videos.setStart(start);
        videos.setLimit(limit);
        for (final Video video : this.manager.getVideos(new VideoQuery().client(client).start(start).limit(limit))) {
            videos.addVideo(new JaxbVideo(video));
        }

        // return the response
        return Response.ok(videos).build();
    }
}
