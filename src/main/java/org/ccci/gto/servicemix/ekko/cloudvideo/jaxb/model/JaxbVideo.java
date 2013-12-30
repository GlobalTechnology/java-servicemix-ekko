package org.ccci.gto.servicemix.ekko.cloudvideo.jaxb.model;

import java.net.URL;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import org.ccci.gto.servicemix.ekko.cloudvideo.AwsController;
import org.ccci.gto.servicemix.ekko.cloudvideo.model.Video;
import org.ccci.gto.servicemix.ekko.cloudvideo.model.Video.State;

@XmlRootElement(name = "video")
public class JaxbVideo {
    @XmlAttribute(name = "id")
    private long id;
    @XmlAttribute(name = "title")
    private String title;
    @XmlAttribute(name = "state")
    private State state;
    @XmlAttribute(name = "thumbnail")
    private String thumbnail;

    public JaxbVideo() {
    }

    public JaxbVideo(final Video video) {
        this(video, (URL) null);
    }

    public JaxbVideo(final Video video, final AwsController aws) {
        this(video, aws != null && video != null ? aws.getSignedUrl(video.getThumbnail()) : (URL) null);
    }

    public JaxbVideo(final Video video, final URL thumbnail) {
        this.id = video.getId();
        this.title = video.getTitle();
        this.state = video.getState();
        this.thumbnail = thumbnail != null ? thumbnail.toString() : null;
    }
}
