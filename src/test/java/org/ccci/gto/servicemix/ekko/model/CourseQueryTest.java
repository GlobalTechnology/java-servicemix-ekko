package org.ccci.gto.servicemix.ekko.model;

import static org.junit.Assert.assertEquals;

import java.security.SecureRandom;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

import org.ccci.gto.servicemix.ekko.TestAssemblyUtils;
import org.ccci.gto.servicemix.ekko.model.Course.CourseQuery;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class CourseQueryTest {
    private static final SecureRandom RAND = new SecureRandom();

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
                course.addEnrolled("3A3CB21C-752E-0651-A41B-6DBE868C8576");
                course.addEnrolled("8F612500-7977-541D-FC38-2AF75974729F");
                em.persist(course);
                em.flush();
                em.clear();
            }

            // fetch course with loadEnrolled
            {
                final List<Course> courses = new CourseQuery().id(id).loadEnrolled(true).execute(em);
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
                final List<Course> courses = new CourseQuery().id(id).loadEnrolled(true).execute(em);
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
                course1.addEnrolled("3A3CB21C-752E-0651-A41B-6DBE868C8576");
                course1.addEnrolled("8F612500-7977-541D-FC38-2AF75974729F");
                em.persist(course1);
                final Course course2 = new Course();
                course2.setId(id + 1);
                course2.addEnrolled("3A3CB21C-752E-0651-A41B-6DBE868C8576");
                course2.addEnrolled("8F612500-7977-541D-FC38-2AF75974729F");
                course2.addEnrolled("01234567-89AB-CDEF-0123-456789ABCDEF");
                em.persist(course2);
                em.flush();
                em.clear();
            }

            // fetch course with loadEnrolled
            {
                final List<Course> courses = new CourseQuery().loadEnrolled(true).execute(em);
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
                course1.addEnrolled("3A3CB21C-752E-0651-A41B-6DBE868C8576");
                course1.addEnrolled("8F612500-7977-541D-FC38-2AF75974729F");
                em.persist(course1);
                final Course course2 = new Course();
                course2.setId(id + 1);
                course2.addEnrolled("3A3CB21C-752E-0651-A41B-6DBE868C8576");
                course2.addEnrolled("8F612500-7977-541D-FC38-2AF75974729F");
                em.persist(course2);
                em.flush();
                em.clear();
            }

            // fetch course with loadEnrolled
            {
                final List<Course> courses = new CourseQuery().loadEnrolled(true).start(0).limit(1).execute(em);
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
                final List<Course> courses = new CourseQuery().id(id).published(true).execute(em);
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
                final List<Course> courses = new CourseQuery().id(id).published(true).execute(em);
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
}
