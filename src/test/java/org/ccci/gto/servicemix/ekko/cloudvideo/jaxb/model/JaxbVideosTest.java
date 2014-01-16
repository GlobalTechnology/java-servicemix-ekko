package org.ccci.gto.servicemix.ekko.cloudvideo.jaxb.model;

import static org.ccci.gto.servicemix.ekko.TestUtils.generateVideos;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;

import javax.ws.rs.core.MediaType;

import org.apache.cxf.jaxrs.impl.MetadataMap;
import org.apache.cxf.jaxrs.provider.JAXBElementProvider;
import org.apache.cxf.jaxrs.provider.json.JSONProvider;
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
public class JaxbVideosTest {
    @Autowired
    JSONProvider<JaxbVideosJson> jsonProvider = null;

    @Autowired
    JAXBElementProvider<JaxbVideosXml> xmlProvider = null;

    private <T extends JaxbVideos> T populateJaxbVideos(final T jaxbVideos, final List<Video> videos) {
        jaxbVideos.setStart(0);
        jaxbVideos.setLimit(videos.size());
        jaxbVideos.setTotal(videos.size() * 2);
        for (final Video video : videos) {
            jaxbVideos.addVideo(new JaxbVideo(video));
        }

        return jaxbVideos;
    }

    @Test
    public void testJsonMarshalling() throws Exception {
        final List<Video> videos = generateVideos();

        // test marshalling for a single video
        testJsonMarshalling(Collections.singletonList(videos.get(0)));

        // generate json for multiple videos
        testJsonMarshalling(videos);
    }

    private void testJsonMarshalling(final List<Video> videos) throws Exception {
        final JsonPath json = toJson(populateJaxbVideos(new JaxbVideosJson(), videos));

        // test generated json
        assertEquals(0, json.getInt("start"));
        assertEquals(videos.size(), json.getInt("limit"));
        assertEquals(videos.size() * 2, json.getInt("total"));

        // test for videos in the generated json
        assertTrue("videos is not an array", json.get("videos") instanceof List);
        assertEquals(videos.size(), json.getInt("videos.size()"));
        for (int i = 0; i < videos.size(); i++) {
            final String base = "videos[" + Integer.valueOf(i).toString() + "]";
            final Video video = videos.get(i);
            assertEquals(video.getId(), json.getLong(base + ".id"));
        }
    }

    @Test
    public void testXmlMarshalling() throws Exception {
        // generate xml for multiple videos
        final List<Video> videos = generateVideos();
        final XmlPath xml = toXml(populateJaxbVideos(new JaxbVideosXml(), videos));

        // test generated xml
        assertEquals("videos", xml.getString("videos.name()"));
        assertEquals(0, xml.getInt("videos.@start"));
        assertEquals(videos.size(), xml.getInt("videos.@limit"));
        assertEquals(videos.size() * 2, xml.getInt("videos.@total"));

        // test for videos in the generated xml
        assertEquals(videos.size(), xml.getInt("videos.video.size()"));
        for (int i = 0; i < videos.size(); i++) {
            final String base = "videos.video[" + Integer.valueOf(i).toString() + "]";
            final Video video = videos.get(i);
            assertEquals(video.getId(), xml.getLong(base + ".@id"));
        }
    }

    private JsonPath toJson(final JaxbVideosJson obj) throws Exception {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        jsonProvider.writeTo(obj, JaxbVideosJson.class, JaxbVideosJson.class, new Annotation[0],
                MediaType.APPLICATION_JSON_TYPE, new MetadataMap<String, Object>(), baos);
        return new JsonPath(baos.toString());
    }

    private XmlPath toXml(final JaxbVideosXml obj) throws Exception {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        xmlProvider.writeTo(obj, JaxbVideosXml.class, JaxbVideosXml.class, new Annotation[0],
                MediaType.APPLICATION_XML_TYPE, new MetadataMap<String, Object>(), baos);
        return new XmlPath(baos.toString());
    }
}
