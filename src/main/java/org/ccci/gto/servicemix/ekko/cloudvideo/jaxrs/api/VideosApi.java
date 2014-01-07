package org.ccci.gto.servicemix.ekko.cloudvideo.jaxrs.api;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;
import static org.ccci.gto.servicemix.common.jaxrs.api.Constants.PATH_API_KEY;
import static org.ccci.gto.servicemix.ekko.cloudvideo.jaxrs.api.Constants.PARAM_GROUP;
import static org.ccci.gto.servicemix.ekko.cloudvideo.jaxrs.api.Constants.PARAM_LIMIT;
import static org.ccci.gto.servicemix.ekko.cloudvideo.jaxrs.api.Constants.PARAM_START;
import static org.ccci.gto.servicemix.ekko.cloudvideo.jaxrs.api.Constants.PARAM_TITLE;

import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.ccci.gto.persistence.FoundRowsList;
import org.ccci.gto.servicemix.common.model.Client;
import org.ccci.gto.servicemix.ekko.cloudvideo.jaxb.model.JaxbVideo;
import org.ccci.gto.servicemix.ekko.cloudvideo.jaxb.model.JaxbVideos;
import org.ccci.gto.servicemix.ekko.cloudvideo.jaxb.model.JaxbVideosJson;
import org.ccci.gto.servicemix.ekko.cloudvideo.jaxb.model.JaxbVideosXml;
import org.ccci.gto.servicemix.ekko.cloudvideo.model.Video;
import org.ccci.gto.servicemix.ekko.cloudvideo.model.Video.VideoQuery;

@Path(PATH_API_KEY + "/videos")
public class VideosApi extends AbstractApi {
    @POST
    @Produces({ APPLICATION_XML, APPLICATION_JSON })
    public Response createVideo(@Context final UriInfo uri, final MultivaluedMap<String, String> form) {
        final Client client = this.getClient(uri);
        if (client == null) {
            return unauthorized().build();
        }

        // create a new video object
        final Video tmp = new Video(client);
        tmp.setGrouping(form.getFirst(PARAM_GROUP));
        tmp.setTitle(form.getFirst(PARAM_TITLE));
        final Video video = this.manager.createVideo(tmp);

        // return the new video object
        return Response.ok(new JaxbVideo(video, this.awsController)).build();
    }

    @GET
    @Produces({ APPLICATION_JSON })
    public Response getVideosJson(@Context UriInfo uri) {
        return this.getVideos(uri, true);
    }

    @GET
    @Produces({ APPLICATION_XML })
    public Response getVideosXml(@Context UriInfo uri) {
        return this.getVideos(uri, false);
    }

    private Response getVideos(@Context final UriInfo uri, final boolean json) {
        final Client client = this.getClient(uri);
        if (client == null) {
            return unauthorized().build();
        }

        // parse & sanitize start and limit
        final MultivaluedMap<String, String> params = uri.getQueryParameters();
        int start = 0;
        try {
            start = Integer.parseInt(params.getFirst(PARAM_START));
        } catch (final Exception ignored) {
        }
        if (start < 0) {
            start = 0;
        }
        int limit = 40;
        try {
            limit = Integer.parseInt(params.getFirst(PARAM_LIMIT));
        } catch (final Exception ignored) {
        }
        if (limit < 0) {
            limit = 40;
        } else if (limit > 80) {
            limit = 40;
        }

        // fetch the videos being requested
        final VideoQuery query = this.getVideoQuery(client).grouping(this.getGroup(uri)).start(start).limit(limit)
                .calcFoundRows(true);
        final List<Video> videos = this.manager.getVideos(query);

        // generate response data
        final JaxbVideos jaxbVideos = json ? new JaxbVideosJson() : new JaxbVideosXml();
        jaxbVideos.setStart(start);
        jaxbVideos.setLimit(limit);
        jaxbVideos.setTotal(videos instanceof FoundRowsList ? ((FoundRowsList<Video>) videos).getFoundRows() : -1);
        for (final Video video : videos) {
            jaxbVideos.addVideo(new JaxbVideo(video, this.awsController));
        }

        // return the response
        return Response.ok(jaxbVideos).build();
    }
}
