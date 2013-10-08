package org.ccci.gto.servicemix.ekko;

import java.io.InputStream;

import org.ccci.gto.servicemix.ekko.model.Course;
import org.ccci.gto.servicemix.ekko.model.Resource;

public interface ResourceManager {
    Resource storeResource(Course course, String mimeType, InputStream in) throws ResourceAlreadyExistsException;

    InputStream loadResource(Resource resource) throws ResourceException;

    void removeResource(Resource resource);

    void removeUnpublishedResources(Course course);

    Resource generateCourseZip(Course course);
}
