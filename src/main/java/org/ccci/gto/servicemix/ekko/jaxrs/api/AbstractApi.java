package org.ccci.gto.servicemix.ekko.jaxrs.api;

import static org.ccci.gto.servicemix.common.jaxrs.api.Constants.PARAM_SESSION;
import static org.ccci.gto.servicemix.ekko.jaxrs.api.Constants.PARAM_COURSE;
import static org.ccci.gto.servicemix.ekko.jaxrs.api.Constants.PARAM_RESOURCE_SHA1;

import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import org.ccci.gto.servicemix.common.jaxrs.api.SessionAwareApi;
import org.ccci.gto.servicemix.ekko.CourseManager;
import org.ccci.gto.servicemix.ekko.model.Course.CourseQuery;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class AbstractApi extends SessionAwareApi {
    @Autowired
    protected CourseManager courseManager;

    public void setCourseManager(final CourseManager courseManager) {
        this.courseManager = courseManager;
    }

    protected UriBuilder getCourseUriBuilder(final UriInfo uri) {
        return this.getBaseUriBuilder(uri).path(CourseApi.class);
    }

    protected UriBuilder getResourceUriBuilder(final UriInfo uri) {
        return this.getBaseUriBuilder(uri).path(ResourcesApi.class).path(ResourcesApi.class, "getResourceBySha1");
    }

    protected Map<String, Object> getUriValues(final UriInfo uri) {
        final Map<String, Object> uriValues = new HashMap<String, Object>();
        for (final String param : new String[] { PARAM_SESSION, PARAM_COURSE, PARAM_RESOURCE_SHA1 }) {
            uriValues.put(param, uri.getPathParameters().getFirst(param));
        }
        return uriValues;
    }

    protected Long getCourseId(final UriInfo uri) {
        return Long.valueOf(uri.getPathParameters().getFirst(PARAM_COURSE));
    }

    protected CourseQuery getCourseQuery(final UriInfo uri) {
        return new CourseQuery().id(this.getCourseId(uri));
    }
}
