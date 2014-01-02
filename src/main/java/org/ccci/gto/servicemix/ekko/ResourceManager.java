package org.ccci.gto.servicemix.ekko;

import java.io.InputStream;

import org.ccci.gto.servicemix.ekko.model.Course;
import org.ccci.gto.servicemix.ekko.model.FileResource;

public interface ResourceManager {
    FileResource storeResource(Course course, String mimeType, InputStream in) throws ResourceAlreadyExistsException;

    InputStream loadResource(FileResource resource) throws ResourceException;

    void removeResource(FileResource resource);

    void removeUnpublishedResources(Course course);

    FileResource generateCourseZip(Course course);
}
