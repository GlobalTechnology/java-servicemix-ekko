package org.ccci.gto.servicemix.ekko.model;

import static org.ccci.gto.servicemix.ekko.Constants.GUID_GUEST;
import static org.ccci.gto.servicemix.ekko.TestConstants.GUID1;
import static org.ccci.gto.servicemix.ekko.TestConstants.GUID2;
import static org.ccci.gto.servicemix.ekko.model.Course.ENROLLMENT_APPROVAL;
import static org.ccci.gto.servicemix.ekko.model.Course.ENROLLMENT_DISABLED;
import static org.ccci.gto.servicemix.ekko.model.Course.ENROLLMENT_OPEN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;

import org.junit.Test;

public class CourseTest {
    @Test
    public void testEnrollment() {
        // test setting all enrollment states
        {
            final Course course = new Course();

            // public & disabled
            course.setPublic(true);
            course.setEnrollment(ENROLLMENT_DISABLED);
            assertTrue(course.isPublic());
            assertTrue(course.isEnrollment(ENROLLMENT_DISABLED));
            assertFalse(course.isEnrollment(ENROLLMENT_OPEN));
            assertFalse(course.isEnrollment(ENROLLMENT_APPROVAL));

            // public & open
            course.setPublic(true);
            course.setEnrollment(ENROLLMENT_OPEN);
            assertTrue(course.isPublic());
            assertFalse(course.isEnrollment(ENROLLMENT_DISABLED));
            assertTrue(course.isEnrollment(ENROLLMENT_OPEN));
            assertFalse(course.isEnrollment(ENROLLMENT_APPROVAL));

            // public & approval
            course.setPublic(true);
            course.setEnrollment(ENROLLMENT_APPROVAL);
            assertTrue(course.isPublic());
            assertFalse(course.isEnrollment(ENROLLMENT_DISABLED));
            assertFalse(course.isEnrollment(ENROLLMENT_OPEN));
            assertTrue(course.isEnrollment(ENROLLMENT_APPROVAL));

            // private & disabled
            course.setPublic(false);
            // should force course to public
            course.setEnrollment(ENROLLMENT_DISABLED);
            assertTrue(course.isPublic());
            assertTrue(course.isEnrollment(ENROLLMENT_DISABLED));
            assertFalse(course.isEnrollment(ENROLLMENT_OPEN));
            assertFalse(course.isEnrollment(ENROLLMENT_APPROVAL));

            // private & open
            course.setPublic(false);
            // should force course to public
            course.setEnrollment(ENROLLMENT_OPEN);
            assertTrue(course.isPublic());
            assertFalse(course.isEnrollment(ENROLLMENT_DISABLED));
            assertTrue(course.isEnrollment(ENROLLMENT_OPEN));
            assertFalse(course.isEnrollment(ENROLLMENT_APPROVAL));

            // private & approval
            course.setPublic(false);
            course.setEnrollment(ENROLLMENT_APPROVAL);
            assertFalse(course.isPublic());
            assertFalse(course.isEnrollment(ENROLLMENT_DISABLED));
            assertFalse(course.isEnrollment(ENROLLMENT_OPEN));
            assertTrue(course.isEnrollment(ENROLLMENT_APPROVAL));
        }
    }

    @Test
    public void testIsContentVisibleTo() {
        final Course course = new Course();
        course.setManifest("simple");

        // test access for admins
        assertFalse(course.isContentVisibleTo(GUID1));
        course.addAdmin(GUID1);
        assertTrue(course.isContentVisibleTo(GUID1));
        assertFalse(course.isContentVisibleTo(GUID2));
        course.setAdmins(null);

        // test access for enrolled
        assertFalse(course.isContentVisibleTo(GUID1));
        course.addEnrolled(GUID1);
        assertTrue(course.isContentVisibleTo(GUID1));
        assertFalse(course.isContentVisibleTo(GUID2));
        course.setEnrolled(null);

        // test access for enrollment types
        assertFalse(course.isContentVisibleTo(GUID1));
        assertFalse(course.isContentVisibleTo(GUID2));
        course.setEnrollment(ENROLLMENT_DISABLED);
        assertTrue(course.isContentVisibleTo(GUID1));
        assertTrue(course.isContentVisibleTo(GUID2));
        course.setEnrollment(ENROLLMENT_OPEN);
        assertFalse(course.isContentVisibleTo(GUID1));
        assertFalse(course.isContentVisibleTo(GUID2));
        course.setEnrollment(ENROLLMENT_APPROVAL);
        assertFalse(course.isContentVisibleTo(GUID1));
        assertFalse(course.isContentVisibleTo(GUID2));

        // test not published
        course.addAdmin(GUID1);
        course.addEnrolled(GUID1);
        course.setEnrollment(ENROLLMENT_OPEN);
        assertTrue(course.isContentVisibleTo(GUID1));
        assertFalse(course.isContentVisibleTo(GUID2));
        course.setManifest(null);
        assertFalse(course.isContentVisibleTo(GUID1));
        assertFalse(course.isContentVisibleTo(GUID2));
    }

    @Test
    public void testIsVisibleTo() {
        final Course course = new Course();
        course.setManifest("asdf");
        course.setPublic(false);

        // test access for admins
        assertFalse(course.isVisibleTo(GUID1));
        course.addAdmin(GUID1);
        assertTrue(course.isVisibleTo(GUID1));
        assertFalse(course.isVisibleTo(GUID2));
        course.setAdmins(null);

        // test access for enrolled
        assertFalse(course.isVisibleTo(GUID1));
        course.addEnrolled(GUID1);
        assertTrue(course.isVisibleTo(GUID1));
        assertFalse(course.isVisibleTo(GUID2));
        course.setEnrolled(null);

        // test access for public/private visibility
        course.setPublic(false);
        assertFalse(course.isVisibleTo(GUID1));
        assertFalse(course.isVisibleTo(GUID2));
        course.setPublic(true);
        assertTrue(course.isVisibleTo(GUID1));
        assertTrue(course.isVisibleTo(GUID2));

        // test not published
        course.addAdmin(GUID1);
        course.addEnrolled(GUID1);
        course.setPublic(false);
        assertTrue(course.isContentVisibleTo(GUID1));
        assertFalse(course.isContentVisibleTo(GUID2));
        course.setManifest(null);
        assertFalse(course.isContentVisibleTo(GUID1));
        assertFalse(course.isContentVisibleTo(GUID2));
    }

    @Test
    public void testGuestUser() {
        final Course course = new Course();
        course.setManifest("asdf");

        // make sure a GUEST user cannot be pending, enrolled, or an admin
        course.setAdmins(Collections.singleton(GUID_GUEST));
        course.setEnrolled(Collections.singleton(GUID_GUEST));
        course.setPending(Collections.singleton(GUID_GUEST));
        assertEquals(0, course.getAdmins().size());
        assertEquals(0, course.getEnrolled().size());
        assertEquals(0, course.getPending().size());

        // test visibility for various enrollment types
        course.setPublic(true);
        course.setEnrollment(ENROLLMENT_DISABLED);
        assertTrue(course.isVisibleTo(GUID_GUEST));
        assertTrue(course.isContentVisibleTo(GUID_GUEST));
        course.setEnrollment(ENROLLMENT_OPEN);
        assertFalse(course.isVisibleTo(GUID_GUEST));
        assertFalse(course.isContentVisibleTo(GUID_GUEST));
        course.setEnrollment(ENROLLMENT_APPROVAL);
        assertFalse(course.isVisibleTo(GUID_GUEST));
        assertFalse(course.isContentVisibleTo(GUID_GUEST));
        course.setPublic(false);
        course.setEnrollment(ENROLLMENT_APPROVAL);
        assertFalse(course.isVisibleTo(GUID_GUEST));
        assertFalse(course.isContentVisibleTo(GUID_GUEST));
    }
}
