package org.ccci.gto.servicemix.ekko.cloudvideo.jaxrs.api;

public final class Constants {
    public static final String PATH_VIDEO = "video/{videoId:[0-9]+}";

    public static final String PARAM_VIDEO = "videoId";
    public static final String PARAM_GROUP = "group";
    public static final String PARAM_TITLE = "title";
    public static final String PARAM_START = "start";
    public static final String PARAM_LIMIT = "limit";

    public static final String PARAM_S3_BUCKET = "s3_bucket";
    public static final String PARAM_S3_KEY = "s3_key";
    public static final String PARAM_S3_DELETE_SOURCE = "s3_delete_source";

    public static final String PARAM_OUTPUT_TYPE = "type";

    public static final String HEADER_STREAM_URI = "X-Ekko-Stream-Uri";
}
