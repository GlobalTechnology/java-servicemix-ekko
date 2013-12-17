package org.ccci.gto.servicemix.ekko.cloudvideo.jaxrs.api;

public final class Constants {
    public static final String PATH_VIDEO = "video/{videoId:[0-9]+}";

    public static final String PARAM_START = "start";
    public static final String PARAM_LIMIT = "limit";
    public static final String PARAM_VIDEO = "videoId";

    // ECV params
    public static final String PARAM_S3_BUCKET = "s3_bucket";
    public static final String PARAM_S3_KEY = "s3_key";
}
