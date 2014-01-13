package org.ccci.gto.servicemix.ekko.cloudvideo.jaxrs.api;

import static org.ccci.gto.servicemix.ekko.cloudvideo.jaxrs.api.Constants.PARAM_GROUP;
import static org.ccci.gto.servicemix.ekko.cloudvideo.jaxrs.api.Constants.PARAM_VIDEO;

import javax.ws.rs.core.UriInfo;

import org.ccci.gto.servicemix.common.jaxrs.api.ClientAwareApi;
import org.ccci.gto.servicemix.common.model.Client;
import org.ccci.gto.servicemix.ekko.cloudvideo.AwsVideoController;
import org.ccci.gto.servicemix.ekko.cloudvideo.VideoManager;
import org.ccci.gto.servicemix.ekko.cloudvideo.VideoStateMachine;
import org.ccci.gto.servicemix.ekko.cloudvideo.model.Video.VideoQuery;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class AbstractApi extends ClientAwareApi {
    @Autowired
    protected VideoManager manager;

    @Autowired
    protected AwsVideoController awsController;

    @Autowired
    protected VideoStateMachine videoStateMachine;

    protected VideoQuery getVideoQuery(final Client client) {
        return new VideoQuery().client(client);
    }

    protected VideoQuery getVideoQuery(final Client client, final UriInfo uri) {
        return this.getVideoQuery(client).grouping(this.getGroup(uri)).id(this.getVideoId(uri));
    }

    protected String getGroup(final UriInfo uri) {
        return uri.getQueryParameters().getFirst(PARAM_GROUP);
    }

    protected long getVideoId(final UriInfo uri) {
        return Long.valueOf(uri.getPathParameters().getFirst(PARAM_VIDEO));
    }
}
