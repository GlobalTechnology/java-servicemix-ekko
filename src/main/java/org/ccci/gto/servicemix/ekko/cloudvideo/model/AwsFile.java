package org.ccci.gto.servicemix.ekko.cloudvideo.model;

import javax.persistence.Column;
import javax.persistence.Embeddable;

import org.apache.commons.lang.StringUtils;

@Embeddable
public class AwsFile implements Comparable<AwsFile> {
    @Column(name = "awsBucket", length = 255)
    private String bucket;
    @Column(name = "awsKey")
    private String key;

    public AwsFile() {
    }

    public AwsFile(final String bucket, final String key) {
        this.bucket = bucket;
        this.key = key;
    }

    public final String getBucket() {
        return this.bucket;
    }

    public final String getKey() {
        return this.key;
    }

    public final boolean exists() {
        return this.bucket != null && this.key != null;
    }

    @Override
    public int hashCode() {
        int code = 0;
        code = (code * 31) + (bucket != null ? bucket.hashCode() : 0);
        code = (code * 31) + (key != null ? key.hashCode() : 0);
        return code;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof AwsFile) {
            final AwsFile file2 = (AwsFile) obj;
            return StringUtils.equals(this.bucket, file2.bucket) && StringUtils.equals(this.key, file2.key);
        }

        return false;
    }

    @Override
    public int compareTo(final AwsFile o) {
        // compare the bucket first
        int result = this.bucket == null ? (o.bucket != null ? -1 : 0) : (o.bucket == null ? 1 : this.bucket
                .compareTo(o.bucket));

        // compare the key if the buckets match
        if (result == 0) {
            result = this.key == null ? (o.key != null ? -1 : 0) : (o.key == null ? 1 : this.key.compareTo(o.key));
        }

        // return the comparison value
        return result;
    }
}
