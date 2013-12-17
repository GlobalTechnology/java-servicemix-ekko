package org.ccci.gto.servicemix.ekko.cloudvideo.jaxb.model;

import java.util.List;

import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "videos")
public class JaxbVideosXml extends JaxbVideos {
    @Override
    @XmlElementRef
    protected List<JaxbVideo> getVideos() {
        return super.getVideos();
    }
}
