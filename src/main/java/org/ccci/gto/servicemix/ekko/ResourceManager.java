package org.ccci.gto.servicemix.ekko;

import java.io.InputStream;

import org.ccci.gto.servicemix.ekko.model.Course;
import org.ccci.gto.servicemix.ekko.model.Resource;

public interface ResourceManager {
    Resource storeResource(Course course, InputStream in) throws ResourceAlreadyExistsException;
}
