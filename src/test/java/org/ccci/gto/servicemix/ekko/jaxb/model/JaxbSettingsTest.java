package org.ccci.gto.servicemix.ekko.jaxb.model;

import static org.ccci.gto.servicemix.ekko.TestUtils.generateCourses;
import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.lang.annotation.Annotation;

import javax.ws.rs.core.MediaType;

import org.apache.cxf.jaxrs.impl.MetadataMap;
import org.apache.cxf.jaxrs.provider.JAXBElementProvider;
import org.apache.cxf.jaxrs.provider.json.JSONProvider;
import org.ccci.gto.servicemix.ekko.model.Course;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.jayway.restassured.path.json.JsonPath;
import com.jayway.restassured.path.xml.XmlPath;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "classpath:META-INF/spring/jaxrs.xml" })
public class JaxbSettingsTest {
    @Autowired
    JSONProvider<JaxbSettings> jsonProvider = null;

    @Autowired
    JAXBElementProvider<JaxbSettings> xmlProvider = null;

    @Test
    public void testJsonMarshalling() throws Exception {
        // test json generation for multiple course variations
        for (final Course course : generateCourses()) {
            final JsonPath json = toJson(new JaxbSettings(course));
            assertEquals(course.getId(), json.getLong("id"));
            assertEquals(course.isPublic(), json.getBoolean("public"));
            assertEquals(course.getEnrollment(), json.getString("enrollmentType"));
        }
    }

    @Test
    public void testXmlMarshalling() throws Exception {
        // test xml generation for multiple course variations
        for (final Course course : generateCourses()) {
            final XmlPath xml = toXml(new JaxbSettings(course));
            assertEquals("settings", xml.getString("settings.name()"));
            assertEquals(course.getId(), xml.getLong("settings.@id"));
            assertEquals(course.isPublic(), xml.getBoolean("settings.public"));
            assertEquals(course.getEnrollment(), xml.getString("settings.enrollmentType"));
        }
    }

    private JsonPath toJson(final JaxbSettings obj) throws Exception {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        jsonProvider.writeTo(obj, JaxbSettings.class, JaxbSettings.class, new Annotation[0],
                MediaType.APPLICATION_JSON_TYPE, new MetadataMap<String, Object>(), baos);
        return new JsonPath(baos.toString());
    }

    private XmlPath toXml(final JaxbSettings obj) throws Exception {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        xmlProvider.writeTo(obj, JaxbSettings.class, JaxbSettings.class, new Annotation[0],
                MediaType.APPLICATION_JSON_TYPE, new MetadataMap<String, Object>(), baos);
        return new XmlPath(baos.toString());
    }
}
