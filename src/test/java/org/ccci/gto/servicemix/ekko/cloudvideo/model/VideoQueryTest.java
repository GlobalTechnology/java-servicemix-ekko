package org.ccci.gto.servicemix.ekko.cloudvideo.model;

import static org.ccci.gto.servicemix.ekko.TestAssemblyUtils.closeEntityManager;
import static org.ccci.gto.servicemix.ekko.TestAssemblyUtils.getEntityManager;
import static org.ccci.gto.servicemix.ekko.TestUtils.generateVideos;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.persistence.EntityManager;

import org.ccci.gto.persistence.FoundRowsList;
import org.ccci.gto.servicemix.common.model.Client;
import org.ccci.gto.servicemix.ekko.TestAssemblyUtils;
import org.ccci.gto.servicemix.ekko.cloudvideo.model.Video.VideoQuery;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class VideoQueryTest {
    private static final SecureRandom RAND = new SecureRandom();

    private static final Client[] CLIENTS = new Client[2];
    static {
        final String grouping = "ekko";
        long id = RAND.nextLong();
        for (int i = 0; i < CLIENTS.length; i++) {
            CLIENTS[i] = new Client(grouping, id++);
        }
    }

    @BeforeClass
    public static void openEntityManagerFactory() {
        TestAssemblyUtils.openEntityManagerFactory();
    }

    @AfterClass
    public static void closeEntityManagerFactory() {
        TestAssemblyUtils.closeEntityManagerFactory();
    }

    private static Set<Long> extractIds(final Collection<Video> videos) {
        final Set<Long> ids = new HashSet<Long>();
        for (final Video video : videos) {
            ids.add(video.getId());
        }

        return ids;
    }

    private static void assertValidVideos(final Collection<Video> result, final Collection<Video> valid,
            final Collection<Video> invalid) {
        final Set<Long> resultIds = extractIds(result);
        final Set<Long> validIds = extractIds(valid);
        final Set<Long> invalidIds = extractIds(invalid);

        // check to see if the correct courses were returned
        assertTrue("results are missing a valid Video", resultIds.containsAll(validIds));
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
    public void testId() {
        final EntityManager em = getEntityManager();

        final List<Video> videos = generateVideos(Collections.singleton(CLIENTS[RAND.nextInt(CLIENTS.length)]));
        final Video target = videos.get(RAND.nextInt(videos.size()));

        // test a wide array of possible videos
        testCondition(em, videos, new VideoQuery().id(target.getId()), new Condition() {
            @Override
            public boolean test(final Video video) {
                return video.getId() == target.getId();
            }
        });

        closeEntityManager(em);
    }

    @Test
    public void testClientId() {
        final EntityManager em = getEntityManager();

        // test a wide array of possible videos
        for (final Client client : CLIENTS) {
            testCondition(em, new VideoQuery().client(client), new Condition() {
                @Override
                public boolean test(final Video video) {
                    return video.getClientId() == client.getId();
                }
            });
        }

        closeEntityManager(em);
    }

    @Test
    public void testDeleted() {
        final EntityManager em = getEntityManager();

        // test a wide array of possible videos
        testCondition(em, new VideoQuery().deleted(true), new Condition() {
            @Override
            public boolean test(final Video video) {
                return video.isDeleted();
            }
        });
        testCondition(em, new VideoQuery().deleted(false), new Condition() {
            @Override
            public boolean test(final Video video) {
                return !video.isDeleted();
            }
        });

        closeEntityManager(em);
    }

    @Test
    public void testFoundRows() {
        final EntityManager em = getEntityManager();

        em.getTransaction().begin();

        // create a bunch of videos
        final List<Video> videos = generateVideos(Arrays.asList(CLIENTS));
        for (final Video video : videos) {
            em.persist(video);
        }
        em.flush();
        em.clear();

        final int total = videos.size();

        // test queries that can be correctly calculated
        for (final VideoQuery query : new VideoQuery[] { new VideoQuery(), // no start or limit
                new VideoQuery().start(1), // start < total
                new VideoQuery().start(total - 1), // start < total
                new VideoQuery().limit(total + 1), // limit > total
                new VideoQuery().start(1).limit(total), // start + limit > total
                new VideoQuery().limit(1).calcFoundRows(true), // force calculation
        }) {
            final List<Video> results = query.execute(em);
            assertTrue(results instanceof FoundRowsList);
            assertEquals(total, ((FoundRowsList<Video>) results).getFoundRows());
        }

        // test queries that can't be calculated
        for (final VideoQuery query : new VideoQuery[] { new VideoQuery().start(total), // start > total
                new VideoQuery().limit(1), // limit < total
                new VideoQuery().limit(total - 1), // limit < total
                new VideoQuery().limit(total), // limit = total
                new VideoQuery().start(1).limit(1), // start + limit < total
                new VideoQuery().start(1).limit(total - 2), // start + limit < total
                new VideoQuery().start(1).limit(total - 1), // start + limit = total
        }) {
            final List<Video> results = query.execute(em);
            assertFalse(results instanceof FoundRowsList);
        }

        // don't save db changes
        em.getTransaction().rollback();

        closeEntityManager(em);
    }

    private void testCondition(final EntityManager em, final VideoQuery query, final Condition condition) {
        testCondition(em, generateVideos(Arrays.asList(CLIENTS)), query, condition);
    }

    private void testCondition(final EntityManager em, final Collection<Video> videos, final VideoQuery query,
            final Condition condition) {
        // generate several test courses
        final List<Video> positive = new ArrayList<Video>();
        final List<Video> negative = new ArrayList<Video>();
        for (final Video course : videos) {
            // put course into correct bucket
            if (condition.test(course)) {
                positive.add(course);
            } else {
                negative.add(course);
            }
        }

        em.getTransaction().begin();

        // persist all courses
        for (final Video video : videos) {
            em.persist(video);
        }
        em.flush();
        em.clear();

        // fetch matching videos
        final List<Video> results = query.calcFoundRows(true).clone().execute(em);
        em.flush();
        em.clear();

        // check that found rows was correctly calculated
        assertTrue(results instanceof FoundRowsList);
        assertEquals(positive.size(), ((FoundRowsList<Video>) results).getFoundRows());

        // check to see if the correct videos were returned
        assertValidVideos(results, positive, negative);

        // don't save db changes
        em.getTransaction().rollback();
    }

    private interface Condition {
        boolean test(Video video);
    }
}
