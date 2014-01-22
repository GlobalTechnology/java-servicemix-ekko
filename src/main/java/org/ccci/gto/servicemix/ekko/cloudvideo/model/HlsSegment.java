package org.ccci.gto.servicemix.ekko.cloudvideo.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.Embedded;

@Embeddable
public class HlsSegment {
    @Embedded
    private AwsFile file;

    @Column(name = "duration", precision = 11, scale = 6)
    private BigDecimal duration;

    public HlsSegment() {
    }

    public final BigDecimal getDuration() {
        return this.duration;
    }

    public final AwsFile getFile() {
        return this.file;
    }

    public final void setDuration(final BigDecimal duration) {
        this.duration = duration != null ? duration.setScale(6, RoundingMode.HALF_UP) : null;
    }

    public final void setFile(final AwsFile file) {
        this.file = file;
    }
}
