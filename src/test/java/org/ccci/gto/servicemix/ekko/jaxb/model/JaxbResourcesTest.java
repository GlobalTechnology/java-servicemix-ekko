package org.ccci.gto.servicemix.ekko.jaxb.model;

import static org.ccci.gto.servicemix.ekko.TestUtils.generateResources;
import static org.ccci.gto.servicemix.ekko.TestUtils.getXmlString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;

import javax.ws.rs.core.MediaType;

import org.apache.cxf.jaxrs.impl.MetadataMap;
import org.apache.cxf.jaxrs.provider.JAXBElementProvider;
import org.apache.cxf.jaxrs.provider.json.JSONProvider;
import org.ccci.gto.servicemix.ekko.model.FileResource;
import org.ccci.gto.servicemix.ekko.model.Resource;
import org.ccci.gto.servicemix.ekko.model.VideoResource;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.jayway.restassured.path.json.JsonPath;
import com.jayway.restassured.path.xml.XmlPath;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "classpath:META-INF/spring/jaxrs.xml" })
public class JaxbResourcesTest {
    @Autowired
    JSONProvider<JaxbResources> jsonProvider = null;

    @Autowired
    JAXBElementProvider<JaxbResources> xmlProvider = null;

    private JaxbResources createJaxbResources(final List<Resource> resources) {
        final JaxbResources jaxbResources = new JaxbResources();
        for (final Resource resource : resources) {
            if (resource instanceof FileResource) {
                jaxbResources.addResource(new JaxbFileResource((FileResource) resource));
            } else if (resource instanceof VideoResource) {
                jaxbResources.addResource(new JaxbVideoResource((VideoResource) resource));
            }
        }

        return jaxbResources;
    }

    // TODO: @Test
    public void testJsonMarshalling() throws Exception {
        final List<Resource> resources = generateResources();
        final JaxbResources jaxbResources = createJaxbResources(resources);
        final JsonPath json = toJson(jaxbResources);

        fail("json test not implemented yet!!!!!");
    }

    @Test
    public void testXmlMarshalling() throws Exception {
        final List<Resource> resources = generateResources();
        Collections.shuffle(resources);
        final JaxbResources jaxbResources = createJaxbResources(resources);
        final XmlPath xml = toXml(jaxbResources);

        // test generated xml
        assertEquals("resources", xml.getString("resources.name()"));

        // test resources in the generated xml
        final String base = "resources.'*'.findAll { it.name() == 'file' || it.name() == 'video' }";
        xml.setRoot(base);
        assertEquals(resources.size(), xml.getInt("size()"));
        for (int i = 0; i < resources.size(); i++) {
            final Resource resource = resources.get(i);
            xml.setRoot(base + "[" + i + "]");
            assertEquals(resource.isPublished(), xml.getBoolean("@published"));
            if (resource instanceof FileResource) {
                assertEquals("file", xml.getString("name()"));
                assertEquals(((FileResource) resource).getSha1(), getXmlString(xml, "@sha1"));
                assertEquals(((FileResource) resource).getSize(), xml.getLong("@size"));
            } else if (resource instanceof VideoResource) {
                assertEquals("video", xml.getString("name()"));
                assertEquals(((VideoResource) resource).getVideoId(), xml.getLong("@id"));
            } else {
                // TODO: implement VideoResource tests
                fail();
            }
        }
    }

    private JsonPath toJson(final JaxbResources obj) throws Exception {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        jsonProvider.writeTo(obj, JaxbResources.class, JaxbResources.class, new Annotation[0],
                MediaType.APPLICATION_JSON_TYPE, new MetadataMap<String, Object>(), baos);
        return new JsonPath(baos.toString());
    }

    private XmlPath toXml(final JaxbResources obj) throws Exception {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        xmlProvider.writeTo(obj, JaxbResources.class, JaxbResources.class, new Annotation[0],
                MediaType.APPLICATION_XML_TYPE, new MetadataMap<String, Object>(), baos);
        return new XmlPath(baos.toString());
    }
}
