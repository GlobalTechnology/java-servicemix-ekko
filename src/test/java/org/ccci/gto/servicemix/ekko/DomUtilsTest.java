package org.ccci.gto.servicemix.ekko;

import static org.junit.Assert.fail;

import org.junit.Test;
import org.w3c.dom.Document;

public class DomUtilsTest {
    final String[] MANIFESTS_V1_VALID = { "manifest/v1/valid/sample_1.xml", "manifest/v1/valid/ecv.xml",
            "manifest/v1/valid/ecv2.xml" };
    final String[] MANIFESTS_V1_INVALID = { "manifest/v1/invalid/duplicate_id.xml",
            "manifest/v1/invalid/missing_resource.xml", "manifest/v1/invalid/wrong_schema_version.xml" };

    @Test
    public void testValidateV1() throws Exception {
        // iterate through all the valid V1 manifests
        for (final String manifest : MANIFESTS_V1_VALID) {
            final Document dom = DomUtils.parse(DomUtils.class.getResourceAsStream(manifest));
            DomUtils.validate(dom);
        }

        // iterate through all the invalid V1 manifests
        for (final String manifest : MANIFESTS_V1_INVALID) {
            final Document dom = DomUtils.parse(DomUtils.class.getResourceAsStream(manifest));
            try {
                DomUtils.validate(dom);
                fail("manifest " + manifest + " validated successfully when it should have had an error");
            } catch (final Exception e) {
            }
        }
    }
}
