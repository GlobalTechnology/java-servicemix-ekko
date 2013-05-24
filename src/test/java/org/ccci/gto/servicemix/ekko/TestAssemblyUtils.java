package org.ccci.gto.servicemix.ekko;

import java.util.HashMap;
import java.util.Map;

import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

public class TestAssemblyUtils {
    private static final String PERSISTENCE_UNIT = "org.ccci.gto.servicemix.ekko";

    public static Map<String, String> getPersistenceProperties() {
        final Map<String, String> props = new HashMap<String, String>();
        props.put("openjpa.jdbc.SynchronizeMappings", "buildSchema(SchemaAction='drop,add',ForeignKeys=true)");

        // hsqldb config
        props.put("openjpa.jdbc.DBDictionary", "hsql");
        props.put("javax.persistence.jdbc.driver", "org.hsqldb.jdbcDriver");
        props.put("javax.persistence.jdbc.url", "jdbc:hsqldb:mem:ekko");
        props.put("javax.persistence.jdbc.user", "sa");
        props.put("javax.persistence.jdbc.password", "");

        // return the properties
        return props;
    }

    public static EntityManagerFactory getEntityManagerFactory() {
        return Persistence.createEntityManagerFactory(PERSISTENCE_UNIT, getPersistenceProperties());
    }
}
