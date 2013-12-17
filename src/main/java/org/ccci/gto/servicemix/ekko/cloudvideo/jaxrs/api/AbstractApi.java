package org.ccci.gto.servicemix.ekko.cloudvideo.jaxrs.api;

import static org.ccci.gto.servicemix.ekko.cloudvideo.jaxrs.api.Constants.PARAM_GROUPING;
import static org.ccci.gto.servicemix.ekko.cloudvideo.jaxrs.api.Constants.PARAM_VIDEO;

import javax.ws.rs.core.UriInfo;

import org.ccci.gto.servicemix.common.jaxrs.api.ClientAwareApi;
import org.ccci.gto.servicemix.common.model.Client;
import org.ccci.gto.servicemix.ekko.cloudvideo.model.Video.VideoQuery;

public abstract class AbstractApi extends ClientAwareApi {
    protected VideoQuery getVideoQuery(final Client client) {
        return new VideoQuery().client(client);
    }

    protected VideoQuery getVideoQuery(final Client client, final UriInfo uri) {
        return this.getVideoQuery(client).grouping(this.getGrouping(uri)).id(this.getVideoId(uri));
    }

    protected String getGrouping(final UriInfo uri) {
        return uri.getQueryParameters().getFirst(PARAM_GROUPING);
    }

    protected long getVideoId(final UriInfo uri) {
        return Long.valueOf(uri.getPathParameters().getFirst(PARAM_VIDEO));
    }
}
