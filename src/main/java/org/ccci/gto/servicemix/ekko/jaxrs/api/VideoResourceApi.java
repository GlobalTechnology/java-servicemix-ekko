package org.ccci.gto.servicemix.ekko.jaxrs.api;

import static org.ccci.gto.servicemix.common.jaxrs.api.Constants.PATH_SESSION;
import static org.ccci.gto.servicemix.ekko.cloudvideo.jaxrs.api.Constants.HEADER_STREAM_URI;
import static org.ccci.gto.servicemix.ekko.cloudvideo.jaxrs.api.Constants.PARAM_OUTPUT_TYPE;
import static org.ccci.gto.servicemix.ekko.cloudvideo.jaxrs.api.Constants.PARAM_VIDEO;
import static org.ccci.gto.servicemix.ekko.cloudvideo.jaxrs.api.Constants.PATH_VIDEO;
import static org.ccci.gto.servicemix.ekko.jaxrs.api.Constants.PATH_COURSE;

import java.math.RoundingMode;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import org.apache.cxf.jaxrs.ext.MessageContext;
import org.ccci.gto.hls.m3u.model.Media;
import org.ccci.gto.hls.m3u.model.Playlist;
import org.ccci.gto.hls.m3u.model.Stream;
import org.ccci.gto.persistence.tx.TransactionService;
import org.ccci.gto.servicemix.common.model.Session;
import org.ccci.gto.servicemix.ekko.cloudvideo.AwsVideoController;
import org.ccci.gto.servicemix.ekko.cloudvideo.VideoManager;
import org.ccci.gto.servicemix.ekko.cloudvideo.model.AwsFile;
import org.ccci.gto.servicemix.ekko.cloudvideo.model.AwsOutput;
import org.ccci.gto.servicemix.ekko.cloudvideo.model.AwsOutput.Type;
import org.ccci.gto.servicemix.ekko.cloudvideo.model.HlsSegment;
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
    private AwsVideoController awsController;

    @Autowired
    private VideoManager videoManager;

    @Autowired
    private TransactionService txService;

    @GET
    @Path("download")
    public Response downloadVideo(@Context final MessageContext cxt, @Context final UriInfo uri) {
        // validate the session
        final Session session = this.getSession(uri);
        if (session == null || session.isExpired()) {
            return this.unauthorized(cxt, uri).build();
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

                    // find the highest quality video available to download
                    return findOutput(video, getType(uri, Type.MP4));
                }
            });

            if (output != null && output.isDownloadable()) {
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
    @Path("stream.m3u8")
    @Produces("application/vnd.apple.mpegurl")
    public Response getHlsStream(@Context final MessageContext cxt, @Context final UriInfo uri) {
        // throw an error if this is not an HLS stream request
        final Type type = this.getType(uri);
        if (!type.isHls()) {
            return Response.status(Status.BAD_REQUEST).build();
        }

        // return the stream
        return this.getStream(cxt, uri);
    }

    @GET
    @Path("stream")
    public Response getStream(@Context final MessageContext cxt, @Context final UriInfo uri) {
        // validate the session
        final Session session = this.getSession(uri);
        if (session == null || session.isExpired()) {
            return this.unauthorized(cxt, uri).build();
        }

        // ensure we have a video
        final Video video = this.getVideo(uri, session.getGuid());
        if (video == null) {
            return Response.status(Status.NOT_FOUND).build();
        }

        // switch based on type requested
        final Type type = this.getType(uri);
        switch (type) {
        case MP4:
        case MP4_720P:
        case MP4_480P_16_9:
            final AwsOutput output = findOutput(video, type);
            if (output != null) {
                try {
                    final URL url = this.awsController.getSignedUrl(output.getFile());
                    return Response.temporaryRedirect(url.toURI()).header(HEADER_STREAM_URI, url).build();
                } catch (final Exception ignored) {
                }
            }
            break;
        case HLS:
        case HLS_2M:
        case HLS_1M:
        case HLS_400K:
            try {
                final Playlist playlist;
                if (type == Type.HLS) {
                    this.getRequestUriBuilder(cxt, uri);
                    playlist = buildHlsMasterPlaylist(video, this.getRequestUriBuilder(cxt, uri));
                } else {
                    playlist = buildHlsMediaPlaylist(video, type);
                }

                // return playlist if we have one
                if (playlist != null) {
                    return Response.ok(playlist.toString()).type("application/vnd.apple.mpegurl")
                            .header(HEADER_STREAM_URI, this.getRequestUri(cxt, uri)).build();
                }
            } catch (final Exception ignored) {
            }
            break;
        case UNKNOWN:
        default:
            return Response.status(Status.BAD_REQUEST).build();
        }

        return Response.status(Status.NOT_FOUND).build();
    }

    @GET
    @Path("thumbnail")
    public Response getThumbnail(@Context final MessageContext cxt, @Context final UriInfo uri) {
        // validate the session
        final Session session = this.getSession(uri);
        if (session == null || session.isExpired()) {
            return this.unauthorized(cxt, uri).build();
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
            return this.txService.inReadOnlyTransaction(new Callable<Video>() {
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

    private Type getType(final UriInfo uri) {
        return this.getType(uri, Type.UNKNOWN);
    }

    private Type getType(final UriInfo uri, final Type defaultType) {
        try {
            return Type.valueOf(uri.getQueryParameters().getFirst(PARAM_OUTPUT_TYPE));
        } catch (final Exception e) {
            return defaultType;
        }
    }

    private AwsOutput findOutput(final Video video, final Type type) {
        try {
            return this.txService.inReadOnlyTransaction(new Callable<AwsOutput>() {
                @Override
                public AwsOutput call() throws Exception {
                    final Video managed = videoManager.getManaged(video);
                    if (type == Type.MP4) {
                        // find the highest quality video available to download (preferring non-stale videos)
                        for (final boolean stale : new boolean[] { false, true }) {
                            for (final Type type : new Type[] { Type.MP4_720P, Type.MP4_480P_16_9 }) {
                                final AwsOutput output = managed.getOutput(type);
                                final AwsFile file = output != null ? output.getFile() : null;
                                if (file != null && file.exists() && (stale || !output.isStale())) {
                                    return output;
                                }
                            }
                        }
                    } else {
                        return managed.getOutput(type);
                    }

                    return null;
                }
            });
        } catch (final Exception ignored) {
        }

        return null;
    }

    private Playlist buildHlsMasterPlaylist(final Video video, final UriBuilder baseUri) {
        try {
            return this.txService.inReadOnlyTransaction(new Callable<Playlist>() {
                @Override
                public Playlist call() throws Exception {
                    final Video managed = videoManager.getManaged(video);

                    // find HLS outputs to include in master playlist
                    final List<AwsOutput> outputs = new ArrayList<>();
                    for (final boolean stale : new boolean[] { false, true }) {
                        for (final Type type : new Type[] { Type.HLS_400K, Type.HLS_1M, Type.HLS_2M }) {
                            final AwsOutput output = managed.getOutput(type);
                            if (output != null && (stale || !output.isStale())) {
                                outputs.add(output);
                            }
                        }
                        if (!outputs.isEmpty()) {
                            break;
                        }
                    }

                    // generate playlist if we actually have outputs
                    if (!outputs.isEmpty()) {
                        final Playlist.Builder playlist = Playlist.create();
                        for (final AwsOutput output : outputs) {
                            final Type type = output.getType();
                            playlist.add(Stream.create().program(1).resolution(output.getWidth(), output.getHeight())
                                    .codecs(type.codecs()).bandwidth(type.bandwidth())
                                    .uri(baseUri.clone().replaceQueryParam(PARAM_OUTPUT_TYPE, type).build().toString())
                                    .build());
                        }
                        return playlist.build();
                    }

                    // default to no playlist
                    return null;
                }
            });
        } catch (final Exception ignored) {
            return null;
        }
    }

    private Playlist buildHlsMediaPlaylist(final Video video, final Type type) {
        if (type.isHls()) {
            try {
                return this.txService.inReadOnlyTransaction(new Callable<Playlist>() {
                    @Override
                    public Playlist call() throws Exception {
                        final AwsOutput output = findOutput(video, type);

                        if (output != null) {
                            final Playlist.Builder playlist = Playlist.create().version(3).allowCache(true)
                                    .mediaSequence(0);
                            int targetDuration = 1;

                            for (final HlsSegment segment : output.getSegments()) {
                                final URL url = awsController.getSignedUrl(segment.getFile());
                                if (url != null) {
                                    playlist.add(Media.create().duration(segment.getDuration()).uri(url.toString())
                                            .build());

                                    // update target duration
                                    final int duration = segment.getDuration().setScale(0, RoundingMode.CEILING)
                                            .intValue();
                                    if (duration > targetDuration) {
                                        targetDuration = duration;
                                    }
                                }
                            }

                            return playlist.targetDuration(targetDuration).complete(true).build();
                        }

                        return null;
                    }
                });
            } catch (final Exception ignored) {
            }
        }

        return null;
    }
}
