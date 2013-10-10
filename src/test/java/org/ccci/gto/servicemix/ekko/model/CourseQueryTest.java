package org.ccci.gto.servicemix.ekko.model;

import static org.ccci.gto.servicemix.ekko.TestConstants.GUID1;
import static org.ccci.gto.servicemix.ekko.TestConstants.GUID2;
import static org.ccci.gto.servicemix.ekko.TestConstants.GUID3;
import static org.ccci.gto.servicemix.ekko.model.Course.ENROLLMENT_APPROVAL;
import static org.ccci.gto.servicemix.ekko.model.Course.ENROLLMENT_DISABLED;
import static org.ccci.gto.servicemix.ekko.model.Course.ENROLLMENT_OPEN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

import org.ccci.gto.servicemix.ekko.TestAssemblyUtils;
import org.ccci.gto.servicemix.ekko.model.Course.CourseQuery;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class CourseQueryTest {
    private static final SecureRandom RAND = new SecureRandom();

    private static final String ENROLLMENT_PRIVATE_TESTING = "private-testing";

    private static EntityManagerFactory emf;
    private static EntityManager em;

    @BeforeClass
    public static void openEntityManager() {
        emf = TestAssemblyUtils.getEntityManagerFactory();
        em = emf.createEntityManager();
    }

    @AfterClass
    public static void closeEntityManager() {
        if (em != null) {
            em.close();
            em = null;
        }
        if (emf != null) {
            emf.close();
            emf = null;
        }
    }

    // generate all possible course variations for testing.
    // *all may be approximate
    private List<Course> generateCourses() {
        final List<Course> courses = new ArrayList<Course>();
        long id = RAND.nextLong();

        for (final boolean published : new Boolean[] { true, false }) {
            for (final String enrollment : new String[] { ENROLLMENT_DISABLED, ENROLLMENT_OPEN, ENROLLMENT_APPROVAL,
                    ENROLLMENT_PRIVATE_TESTING, }) {
                for (final boolean admin : new Boolean[] { true, false }) {
                    for (final String guid : new String[] { null, GUID1, GUID2 }) {
                        // generate course
                        final Course course = new Course();
                        course.setId(id++);
                        if (published) {
                            course.setManifest("manifest");
                        }
                        switch (enrollment) {
                        case ENROLLMENT_PRIVATE_TESTING:
                            course.setPublic(false);
                            break;
                        default:
                            course.setEnrollment(enrollment);
                        }
                        if (guid != null) {
                            if (admin) {
                                course.addAdmin(guid);
                            } else {
                                course.addEnrolled(guid);
                            }
                        }

                        courses.add(course);
                    }
                }
            }
        }

        return courses;
    }

    private static Set<Long> extractIds(final Collection<Course> courses) {
        final Set<Long> ids = new HashSet<Long>();
        for (final Course course : courses) {
            ids.add(course.getId());
        }

        return ids;
    }

    private static void assertValidCourses(final Collection<Course> result, final Collection<Course> valid,
            final Collection<Course> invalid) {
        final Set<Long> resultIds = extractIds(result);
        final Set<Long> validIds = extractIds(valid);
        final Set<Long> invalidIds = extractIds(invalid);

        // check to see if the correct courses were returned
        assertTrue("results are missing a valid Course", resultIds.containsAll(validIds));
        assertTrue(validIds.containsAll(validIds));
        for (final Long id : resultIds) {
            assertFalse(invalidIds.contains(id));
        }
        for (final Long id : invalidIds) {
            assertFalse(resultIds.contains(id));
        }
        assertEquals(valid.size(), result.size());
    }

    @Test
    public void testLoadEnrolled() {
        long id = RAND.nextLong();

        // test default usage
        {
            em.getTransaction().begin();

            // create course being tested
            {
                final Course course = new Course();
                course.setId(id);
                course.addEnrolled(GUID1);
                course.addEnrolled(GUID2);
                em.persist(course);
                em.flush();
                em.clear();
            }

            // fetch course with loadEnrolled
            {
                final List<Course> courses = new CourseQuery().id(id).loadEnrolled(true).clone().execute(em);
                em.flush();
                em.clear();
                assertEquals(1, courses.size());
                assertEquals(2, courses.get(0).getEnrolled().size());
            }

            // don't save db changes
            em.getTransaction().rollback();

            // increment id after usage
            id++;
        }

        // test no enrolled
        {
            em.getTransaction().begin();

            // create course being tested
            {
                final Course course = new Course();
                course.setId(id);
                em.persist(course);
                em.flush();
                em.clear();
            }

            // fetch course with loadEnrolled
            {
                final List<Course> courses = new CourseQuery().id(id).loadEnrolled(true).clone().execute(em);
                em.flush();
                em.clear();
                assertEquals(1, courses.size());
                assertEquals(0, courses.get(0).getEnrolled().size());
            }

            // don't save db changes
            em.getTransaction().rollback();

            // increment id after usage
            id++;
        }

        // test usage with multiple courses
        {
            em.getTransaction().begin();

            // create course being tested
            {
                final Course course1 = new Course();
                course1.setId(id);
                course1.addEnrolled(GUID1);
                course1.addEnrolled(GUID2);
                em.persist(course1);
                final Course course2 = new Course();
                course2.setId(id + 1);
                course2.addEnrolled(GUID1);
                course2.addEnrolled(GUID2);
                course2.addEnrolled(GUID3);
                em.persist(course2);
                em.flush();
                em.clear();
            }

            // fetch course with loadEnrolled
            {
                final List<Course> courses = new CourseQuery().loadEnrolled(true).clone().execute(em);
                em.flush();
                em.clear();
                assertEquals(2, courses.size());
                assertEquals(2, courses.get(0).getEnrolled().size());
                assertEquals(3, courses.get(1).getEnrolled().size());
            }

            // don't save db changes
            em.getTransaction().rollback();

            // increment id after usage
            id += 2;
        }

        // test usage with limit
        {
            em.getTransaction().begin();

            // create course being tested
            {
                final Course course1 = new Course();
                course1.setId(id);
                course1.addEnrolled(GUID1);
                course1.addEnrolled(GUID2);
                em.persist(course1);
                final Course course2 = new Course();
                course2.setId(id + 1);
                course2.addEnrolled(GUID1);
                course2.addEnrolled(GUID2);
                em.persist(course2);
                em.flush();
                em.clear();
            }

            // fetch course with loadEnrolled
            {
                final List<Course> courses = new CourseQuery().loadEnrolled(true).start(0).limit(1).clone().execute(em);
                em.flush();
                em.clear();
                assertEquals(1, courses.size());
                assertEquals(2, courses.get(0).getEnrolled().size());
            }

            // don't save db changes
            em.getTransaction().rollback();

            // increment id after usage
            id += 2;
        }
    }

    @Test
    public void testPublished() {
        long id = RAND.nextLong();

        // test published course
        {
            em.getTransaction().begin();

            // create course being tested
            {
                final Course course = new Course();
                course.setId(id);
                course.setManifest("manifest");
                em.persist(course);
                em.flush();
                em.clear();
            }

            // fetch course with published
            {
                final List<Course> courses = new CourseQuery().id(id).published(true).clone().execute(em);
                em.flush();
                em.clear();
                assertEquals(1, courses.size());
            }

            // don't save db changes
            em.getTransaction().rollback();

            // increment id after usage
            id++;
        }

        // test unpublished course (no manifest)
        {
            em.getTransaction().begin();

            // create course being tested
            {
                final Course course = new Course();
                course.setId(id);
                em.persist(course);
                em.flush();
                em.clear();
            }

            // fetch course with published
            {
                final List<Course> courses = new CourseQuery().id(id).published(true).clone().execute(em);
                em.flush();
                em.clear();
                assertEquals(0, courses.size());
            }

            // don't save db changes
            em.getTransaction().rollback();

            // increment id after usage
            id++;
        }
    }

    @Test
    public void testEnrolled() {
        // test several enrolled variations
        {
            // generate several test courses that are enrolled/not enrolled
            final List<Course> enrolled = new ArrayList<Course>();
            final List<Course> notEnrolled = new ArrayList<Course>();
            for (final Course course : this.generateCourses()) {
                // put course into correct bucket
                if (course.isEnrolled(GUID1)) {
                    enrolled.add(course);
                } else {
                    notEnrolled.add(course);
                }
            }

            em.getTransaction().begin();

            // persist all courses
            for (final Course course : enrolled) {
                em.persist(course);
            }
            for (final Course course : notEnrolled) {
                em.persist(course);
            }
            em.flush();
            em.clear();

            // fetch visible courses
            final List<Course> courses = new CourseQuery().enrolled(GUID1).clone().execute(em);
            em.flush();
            em.clear();

            // check to see if the correct courses were returned
            assertValidCourses(courses, enrolled, notEnrolled);

            // don't save db changes
            em.getTransaction().rollback();
        }
    }

    @Test
    public void testVisibleTo() {
        // test several visible variations
        {
            // generate several test courses that are visible/not visible
            final List<Course> visible = new ArrayList<Course>();
            final List<Course> notVisible = new ArrayList<Course>();
            for (final Course course : this.generateCourses()) {
                // put course into correct bucket
                if (course.isVisibleTo(GUID1)) {
                    visible.add(course);
                } else {
                    notVisible.add(course);
                }
            }

            em.getTransaction().begin();

            // persist all courses
            for (final Course course : visible) {
                em.persist(course);
            }
            for (final Course course : notVisible) {
                em.persist(course);
            }
            em.flush();
            em.clear();

            // fetch visible courses
            final List<Course> courses = new CourseQuery().visibleTo(GUID1).clone().execute(em);
            em.flush();
            em.clear();

            // check to see if the correct courses were returned
            assertValidCourses(courses, visible, notVisible);

            // don't save db changes
            em.getTransaction().rollback();
        }
    }

    @Test
    public void testContentVisibleTo() {
        // test several content visible variations
        {
            // generate several test courses that are visible/not visible
            final List<Course> visible = new ArrayList<Course>();
            final List<Course> notVisible = new ArrayList<Course>();
            for (final Course course : this.generateCourses()) {
                // put course into correct bucket
                if (course.isContentVisibleTo(GUID1)) {
                    visible.add(course);
                } else {
                    notVisible.add(course);
                }
            }

            em.getTransaction().begin();

            // persist all courses
            for (final Course course : visible) {
                em.persist(course);
            }
            for (final Course course : notVisible) {
                em.persist(course);
            }
            em.flush();
            em.clear();

            // fetch visible courses
            final List<Course> courses = new CourseQuery().contentVisibleTo(GUID1).clone().execute(em);
            em.flush();
            em.clear();

            // check to see if the correct courses were returned
            assertValidCourses(courses, visible, notVisible);

            // don't save db changes
            em.getTransaction().rollback();
        }
    }
}
