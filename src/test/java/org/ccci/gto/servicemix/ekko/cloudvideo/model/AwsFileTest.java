package org.ccci.gto.servicemix.ekko.cloudvideo.model;

import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.junit.Test;

public class AwsFileTest {
    private List<AwsFile> generateFiles() {
        final List<AwsFile> files = new ArrayList<>();
        for (final String bucket : new String[] { null, "bucket1", "bucket2" }) {
            for (final String key : new String[] { null, "key1", "key2" }) {
                files.add(new AwsFile(bucket, key));
            }
        }

        return files;
    }

    @Test
    public void testEquals() {
        final List<AwsFile> files1 = generateFiles();
        final List<AwsFile> files2 = generateFiles();
        assertArrayEquals(files1.toArray(), files2.toArray());

        for (int i = 0; i < files1.size(); i++) {
            for (int j = 0; j < files2.size(); j++) {
                if (i == j) {
                    assertEquals(files1.get(i), files2.get(j));
                } else {
                    assertThat(files1.get(i), not(files2.get(j)));
                }
            }
        }
    }

    @Test
    public void testHashSets() {
        final HashSet<AwsFile> set = new HashSet<>(generateFiles());
        for (final AwsFile file : generateFiles()) {
            assertTrue(set.contains(file));
        }

        final List<AwsFile> files = generateFiles();
        for (int i = 0; i < files.size(); i++) {
            assertEquals(files.size() - i, set.size());
            assertTrue(set.remove(files.get(i)));
        }
        assertTrue(set.isEmpty());
    }
}
