package org.ccci.gto.servicemix.ekko.model;

import static org.ccci.gto.servicemix.ekko.Constants.GUID_GUEST;
import static org.ccci.gto.servicemix.ekko.TestAssemblyUtils.closeEntityManager;
import static org.ccci.gto.servicemix.ekko.TestAssemblyUtils.getEntityManager;
import static org.ccci.gto.servicemix.ekko.TestConstants.GUID1;
import static org.ccci.gto.servicemix.ekko.TestConstants.GUID2;
import static org.ccci.gto.servicemix.ekko.TestConstants.GUID3;
import static org.ccci.gto.servicemix.ekko.TestUtils.generateCourses;
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

import org.ccci.gto.servicemix.ekko.TestAssemblyUtils;
import org.ccci.gto.servicemix.ekko.model.Course.CourseQuery;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class CourseQueryTest {
    private static final SecureRandom RAND = new SecureRandom();

    @BeforeClass
    public static void openEntityManagerFactory() {
        TestAssemblyUtils.openEntityManagerFactory();
    }

    @AfterClass
    public static void closeEntityManagerFactory() {
        TestAssemblyUtils.closeEntityManagerFactory();
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
        assertTrue(validIds.containsAll(resultIds));
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
        final EntityManager em = getEntityManager();

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

        closeEntityManager(em);
    }

    @Test
    public void testPublished() {
        final EntityManager em = getEntityManager();

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

        // test a wide array of possible courses
        testCondition(em, new CourseQuery().published(), new Condition() {
            @Override
            public boolean test(final Course course) {
                return course.isPublished();
            }
        });

        closeEntityManager(em);
    }

    @Test
    public void testEnrolled() {
        final EntityManager em = getEntityManager();

        for (final String guid : new String[] { GUID_GUEST, GUID1, GUID2, GUID3 }) {
            testCondition(em, new CourseQuery().enrolled(guid), new Condition() {
                @Override
                public boolean test(final Course course) {
                    return course.isEnrolled(guid);
                }
            });
        }

        closeEntityManager(em);
    }

    @Test
    public void testPendingOrEnrolled() {
        final EntityManager em = getEntityManager();

        for (final String guid : new String[] { GUID_GUEST, GUID1, GUID2, GUID3 }) {
            testCondition(em, new CourseQuery().enrolled(guid).pending(guid), new Condition() {
                @Override
                public boolean test(final Course course) {
                    return course.isEnrolled(guid) || course.isPending(guid);
                }
            });
        }

        closeEntityManager(em);
    }

    @Test
    public void testVisibleTo() {
        final EntityManager em = getEntityManager();

        for (final String guid : new String[] { GUID_GUEST, GUID1, GUID2, GUID3 }) {
            testCondition(em, new CourseQuery().visibleTo(guid), new Condition() {
                @Override
                public boolean test(final Course course) {
                    return course.isVisibleTo(guid);
                }
            });
        }

        closeEntityManager(em);
    }

    @Test
    public void testContentVisibleTo() {
        final EntityManager em = getEntityManager();

        for (final String guid : new String[] { GUID_GUEST, GUID1, GUID2, GUID3 }) {
            testCondition(em, new CourseQuery().contentVisibleTo(guid), new Condition() {
                @Override
                public boolean test(final Course course) {
                    return course.isContentVisibleTo(guid);
                }
            });
        }

        closeEntityManager(em);
    }

    private void testCondition(final EntityManager em, final CourseQuery query, final Condition condition) {
        // generate several test courses
        final List<Course> positive = new ArrayList<Course>();
        final List<Course> negative = new ArrayList<Course>();
        for (final Course course : generateCourses()) {
            // put course into correct bucket
            if (condition.test(course)) {
                positive.add(course);
            } else {
                negative.add(course);
            }
        }

        em.getTransaction().begin();

        // persist all courses
        for (final Course course : positive) {
            em.persist(course);
        }
        for (final Course course : negative) {
            em.persist(course);
        }
        em.flush();
        em.clear();

        // fetch matching courses
        final List<Course> courses = query.clone().execute(em);
        em.flush();
        em.clear();

        // check to see if the correct courses were returned
        assertValidCourses(courses, positive, negative);

        // don't save db changes
        em.getTransaction().rollback();
    }

    private interface Condition {
        boolean test(Course course);
    }
}
