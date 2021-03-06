package org.ccci.gto.servicemix.ekko.jaxb.model;

import static org.ccci.gto.servicemix.ekko.TestUtils.generateFileResources;
import static org.ccci.gto.servicemix.ekko.TestUtils.getXmlString;
import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.lang.annotation.Annotation;

import javax.ws.rs.core.MediaType;

import org.apache.cxf.jaxrs.impl.MetadataMap;
import org.apache.cxf.jaxrs.provider.JAXBElementProvider;
import org.apache.cxf.jaxrs.provider.json.JSONProvider;
import org.ccci.gto.servicemix.ekko.model.FileResource;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.jayway.restassured.path.json.JsonPath;
import com.jayway.restassured.path.xml.XmlPath;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "classpath:META-INF/spring/jaxrs.xml" })
public class JaxbFileResourceTest {
    @Autowired
    JSONProvider<JaxbFileResource> jsonProvider = null;

    @Autowired
    JAXBElementProvider<JaxbFileResource> xmlProvider = null;

    // TODO: @Test
    public void testJsonMarshalling() throws Exception {
        // test xml generation for multiple resource variations
        for (final FileResource resource : generateFileResources()) {
            final JsonPath json = toJson(new JaxbFileResource(resource));
            assertEquals(resource.getSha1(), json.getString("resource.@sha1"));
            assertEquals(resource.getSize(), json.getLong("resource.@size"));
            assertEquals(resource.isPublished(), json.getBoolean("resource.@published"));
            // TODO: test uri generation & output
        }
    }

    @Test
    public void testXmlMarshalling() throws Exception {
        // test xml generation for multiple resource variations
        for (final FileResource resource : generateFileResources()) {
            final XmlPath xml = toXml(new JaxbFileResource(resource));
            assertEquals("file", xml.getString("file.name()"));
            assertEquals(resource.getSha1(), getXmlString(xml, "file.@sha1"));
            assertEquals(resource.getSize(), xml.getLong("file.@size"));
            assertEquals(resource.isPublished(), xml.getBoolean("file.@published"));
            // TODO: test uri generation & output
        }
    }

    private JsonPath toJson(final JaxbFileResource obj) throws Exception {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        jsonProvider.writeTo(obj, JaxbFileResource.class, JaxbFileResource.class, new Annotation[0],
                MediaType.APPLICATION_JSON_TYPE, new MetadataMap<String, Object>(), baos);
        return new JsonPath(baos.toString());
    }

    private XmlPath toXml(final JaxbFileResource obj) throws Exception {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        xmlProvider.writeTo(obj, JaxbFileResource.class, JaxbFileResource.class, new Annotation[0],
                MediaType.APPLICATION_XML_TYPE, new MetadataMap<String, Object>(), baos);
        return new XmlPath(baos.toString());
    }
}
