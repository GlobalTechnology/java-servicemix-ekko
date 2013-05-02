package org.ccci.gto.servicemix.ekko.jaxrs.api;

import java.net.URI;

import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import org.ccci.gto.servicemix.cas.jaxrs.api.SessionAwareApi;

public abstract class AbstractApi extends SessionAwareApi {
    private URI baseUri = null;

    public void setBaseUri(final String baseUri) {
        this.baseUri = URI.create(baseUri);
    }

    public void setBaseUri(final URI baseUri) {
        this.baseUri = baseUri;
    }

    public URI getBaseUri() {
        return this.baseUri;
    }

    protected UriBuilder getRequestUri(final UriInfo uri) {
        final URI baseUri = this.getBaseUri();

        if (baseUri == null) {
            return uri.getRequestUriBuilder();
        } else {
            return UriBuilder.fromUri(baseUri.resolve(uri.getBaseUri().relativize(uri.getRequestUri())));
        }
    }
}
