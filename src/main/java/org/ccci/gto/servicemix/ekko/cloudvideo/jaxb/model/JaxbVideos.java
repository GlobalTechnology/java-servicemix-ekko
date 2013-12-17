package org.ccci.gto.servicemix.ekko.cloudvideo.jaxb.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlTransient;

public abstract class JaxbVideos {
    @XmlTransient
    private List<JaxbVideo> videos = new ArrayList<>();

    @XmlAttribute
    private int start = 0;
    @XmlAttribute
    private int limit = 0;
    @XmlAttribute
    private long total = 0;

    public void addVideo(final JaxbVideo video) {
        this.videos.add(video);
    }

    public void setVideos(final Collection<JaxbVideo> videos) {
        this.videos.clear();
        if (videos != null) {
            this.videos.addAll(videos);
        }
    }

    protected List<JaxbVideo> getVideos() {
        return this.videos;
    }

    public void setStart(final int start) {
        this.start = start;
    }

    public void setLimit(final int limit) {
        this.limit = limit;
    }

    public void setTotal(final long total) {
        this.total = total;
    }
}
