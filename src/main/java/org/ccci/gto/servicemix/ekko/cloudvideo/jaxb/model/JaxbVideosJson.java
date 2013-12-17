package org.ccci.gto.servicemix.ekko.cloudvideo.jaxb.model;

import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class JaxbVideosJson extends JaxbVideos {
    @Override
    @XmlElement(name = "videos")
    protected List<JaxbVideo> getVideos() {
        return super.getVideos();
    }
}
