package org.ccci.gto.servicemix.ekko.cloudvideo.util;

import java.util.UUID;

public final class AWSUtils {
    public static String extractName(final String key) {
        assert key != null : "key cannot be null";

        // extract a file name
        final int i = key.lastIndexOf("/");
        if (i >= 0 && i < key.length() - 1) {
            return key.substring(i + 1);
        }

        return key;
    }

    public static String makeUnique(final String name) {
        assert name != null : "name cannot be null";

        final int i = name.lastIndexOf(".");
        final String random = UUID.randomUUID().toString().substring(24);
        if (i >= 0) {
            return name.substring(0, i) + "-" + random + name.substring(i);
        } else {
            return name + "-" + random;
        }
    }
}
