package org.ccci.gto.servicemix.ekko.cloudvideo.jaxb.model;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

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

    public JaxbVideo() {
    }

    public JaxbVideo(final Video video) {
        this.id = video.getId();
        this.title = video.getTitle();
        this.state = video.getState();
    }
}
