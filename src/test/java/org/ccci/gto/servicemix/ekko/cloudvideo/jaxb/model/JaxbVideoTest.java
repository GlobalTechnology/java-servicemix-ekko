package org.ccci.gto.servicemix.ekko.cloudvideo.jaxb.model;

import static org.ccci.gto.servicemix.ekko.TestUtils.generateVideos;
import static org.ccci.gto.servicemix.ekko.TestUtils.getXmlString;
import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.lang.annotation.Annotation;
import java.net.URL;

import javax.ws.rs.core.MediaType;

import org.apache.cxf.jaxrs.impl.MetadataMap;
import org.apache.cxf.jaxrs.provider.JAXBElementProvider;
import org.apache.cxf.jaxrs.provider.json.JSONProvider;
import org.ccci.gto.servicemix.ekko.cloudvideo.model.AwsFile;
import org.ccci.gto.servicemix.ekko.cloudvideo.model.Video;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.jayway.restassured.path.json.JsonPath;
import com.jayway.restassured.path.xml.XmlPath;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "classpath:META-INF/spring/jaxrs.xml" })
public class JaxbVideoTest {
    @Autowired
    JSONProvider<JaxbVideo> jsonProvider = null;

    @Autowired
    JAXBElementProvider<JaxbVideo> xmlProvider = null;

    @Test
    public void testJsonMarshalling() throws Exception {
        // test json generation for multiple video variations
        for (final Video video : generateVideos()) {
            final AwsFile thumb = video.getThumbnail();
            final String thumbUrl = thumb != null && thumb.exists() ? "https://example.com/" + video.getId() : null;
            final JsonPath json = toJson(new JaxbVideo(video, thumbUrl != null ? new URL(thumbUrl) : null));
            assertEquals(video.getId(), json.getLong("id"));
            assertEquals(video.getTitle(), json.getString("title"));
            assertEquals(video.getState().name(), json.getString("state"));
            assertEquals(thumbUrl, json.getString("thumbnail"));
        }
    }

    @Test
    public void testXmlMarshalling() throws Exception {
        // test xml generation for multiple video variations
        for (final Video video : generateVideos()) {
            final AwsFile thumb = video.getThumbnail();
            final String thumbUrl = thumb != null && thumb.exists() ? "https://example.com/" + video.getId() : null;
            final XmlPath xml = toXml(new JaxbVideo(video, thumbUrl != null ? new URL(thumbUrl) : null));
            assertEquals(video.getId(), xml.getLong("video.@id"));
            assertEquals(video.getTitle(), getXmlString(xml, "video.@title"));
            assertEquals(video.getState().name(), xml.getString("video.@state"));
            assertEquals(thumbUrl, getXmlString(xml, "video.@thumbnail"));
        }
    }

    private JsonPath toJson(final JaxbVideo obj) throws Exception {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        jsonProvider.writeTo(obj, JaxbVideo.class, JaxbVideo.class, new Annotation[0],
                MediaType.APPLICATION_JSON_TYPE, new MetadataMap<String, Object>(), baos);
        return new JsonPath(baos.toString());
    }

    private XmlPath toXml(final JaxbVideo obj) throws Exception {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        xmlProvider.writeTo(obj, JaxbVideo.class, JaxbVideo.class, new Annotation[0],
                MediaType.APPLICATION_JSON_TYPE, new MetadataMap<String, Object>(), baos);
        return new XmlPath(baos.toString());
    }
}
