package org.ccci.gto.servicemix.ekko.jaxrs.api;

import static org.ccci.gto.servicemix.common.jaxrs.api.Constants.PATH_SESSION;
import static org.ccci.gto.servicemix.ekko.cloudvideo.jaxrs.api.Constants.PARAM_VIDEO;
import static org.ccci.gto.servicemix.ekko.cloudvideo.jaxrs.api.Constants.PATH_VIDEO;
import static org.ccci.gto.servicemix.ekko.jaxrs.api.Constants.PATH_COURSE;

import java.net.URL;
import java.util.concurrent.Callable;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

import org.ccci.gto.persistence.tx.TransactionService;
import org.ccci.gto.servicemix.common.model.Session;
import org.ccci.gto.servicemix.ekko.cloudvideo.AwsController;
import org.ccci.gto.servicemix.ekko.cloudvideo.VideoManager;
import org.ccci.gto.servicemix.ekko.cloudvideo.model.AwsFile;
import org.ccci.gto.servicemix.ekko.cloudvideo.model.AwsOutput;
import org.ccci.gto.servicemix.ekko.cloudvideo.model.AwsOutput.Type;
import org.ccci.gto.servicemix.ekko.cloudvideo.model.Video;
import org.ccci.gto.servicemix.ekko.model.Course;
import org.ccci.gto.servicemix.ekko.model.VideoResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

@Path(PATH_SESSION + "/courses/" + PATH_COURSE + "/resources/" + PATH_VIDEO)
public class VideoResourceApi extends AbstractApi {
    private static final Logger LOG = LoggerFactory.getLogger(VideoResourceApi.class);

    @Autowired
    private AwsController awsController;

    @Autowired
    private VideoManager videoManager;

    @Autowired
    private TransactionService txService;

    @GET
    @Path("download")
    public Response downloadVideo(@Context final UriInfo uri) {
        // validate the session
        final Session session = this.getSession(uri);
        if (session == null || session.isExpired()) {
            return this.unauthorized(uri).build();
        }

        // generate URL for the requested video
        try {
            // find the output to be returned
            final AwsOutput output = this.txService.inReadOnlyTransaction(new Callable<AwsOutput>() {
                @Override
                public AwsOutput call() throws Exception {
                    // find the video
                    final Video video = getVideo(uri, session.getGuid());
                    if (video == null) {
                        return null;
                    }

                    // find the highest quality video available to download (ignoring stale videos)
                    for (final Type type : new Type[] { Type.MP4_720P, Type.MP4_480P_16_9 }) {
                        final AwsOutput output = video.getOutput(type);
                        if (output != null && !output.isStale()) {
                            final AwsFile file = output.getFile();
                            if (file != null && file.exists()) {
                                return output;
                            }
                        }
                    }

                    // default to no output
                    return null;
                }
            });

            if (output != null) {
                final URL url = this.awsController.getSignedUrl(output.getFile());
                if (url != null) {
                    return Response.temporaryRedirect(url.toURI()).build();
                }
            }
        } catch (final Exception e) {
            LOG.debug("Error generating download url", e);
            return Response.status(Status.NOT_FOUND).build();
        }

        return Response.status(Status.NOT_FOUND).build();
    }

    @GET
    @Path("thumbnail")
    public Response getThumbnail(@Context final UriInfo uri) {
        // validate the session
        final Session session = this.getSession(uri);
        if (session == null || session.isExpired()) {
            return this.unauthorized(uri).build();
        }

        // get the video
        final Video video = this.getVideo(uri, session.getGuid());
        if (video != null) {
            final URL thumbUrl = this.awsController.getSignedUrl(video.getThumbnail());
            if (thumbUrl != null) {
                try {
                    return Response.temporaryRedirect(thumbUrl.toURI()).build();
                } catch (final Exception ignored) {
                }
            }
        }

        return Response.status(Status.NOT_FOUND).build();
    }

    private Video getVideo(final UriInfo uri, final String guid) {
        try {
            return this.txService.inTransaction(new Callable<Video>() {
                @Override
                public Video call() throws Exception {
                    // first find the Course & VideoResource
                    final Course course = courseManager.getCourse(getCourseQuery(uri));
                    final VideoResource resource = course != null ? course.getVideoResource(Long.parseLong(uri
                            .getPathParameters().getFirst(PARAM_VIDEO))) : null;

                    // return the video if it exists and the user is authorized to see it
                    if (resource != null && resource.isVisibleTo(guid)) {
                        return resource.getVideo();
                    }

                    return null;
                }
            });
        } catch (final Exception ignored) {
        }

        return null;
    }
}
