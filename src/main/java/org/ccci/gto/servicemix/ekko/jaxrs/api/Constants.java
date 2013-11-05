package org.ccci.gto.servicemix.ekko.jaxrs.api;

public final class Constants {
    public static final String PATH_COURSE = "course/{courseId:[0-9]+}";
    public static final String PATH_RESOURCE_SHA1 = "resource/{sha1:[0-9a-f]+}";

    public static final String PARAM_COURSE = "courseId";
    public static final String PARAM_START = "start";
    public static final String PARAM_LIMIT = "limit";
    public static final String PARAM_RESOURCE_SHA1 = "sha1";

    // course setting params
    public static final String PARAM_PUBLIC = "public";
    public static final String PARAM_ENROLLMENT_TYPE = "enrollmentType";
}
