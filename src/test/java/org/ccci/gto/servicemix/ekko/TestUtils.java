package org.ccci.gto.servicemix.ekko;

import static org.ccci.gto.servicemix.ekko.Constants.GUID_GUEST;
import static org.ccci.gto.servicemix.ekko.TestConstants.GUID1;
import static org.ccci.gto.servicemix.ekko.TestConstants.GUID2;
import static org.ccci.gto.servicemix.ekko.model.Course.ENROLLMENT_APPROVAL;
import static org.ccci.gto.servicemix.ekko.model.Course.ENROLLMENT_DISABLED;
import static org.ccci.gto.servicemix.ekko.model.Course.ENROLLMENT_OPEN;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import org.ccci.gto.servicemix.common.model.Client;
import org.ccci.gto.servicemix.ekko.cloudvideo.model.Video;
import org.ccci.gto.servicemix.ekko.cloudvideo.model.Video.State;
import org.ccci.gto.servicemix.ekko.model.Course;

public class TestUtils {
    private static final SecureRandom RAND = new SecureRandom();

    private static final String ENROLLMENT_PRIVATE_TESTING = "private-testing";

    private static enum MEMBERSHIP {
        NONE, ADMIN, ENROLLED, ADMIN_ENROLLED, PENDING
    };

    // generate all possible course variations for testing.
    // *all may be approximate
    public static List<Course> generateCourses() {
        final List<Course> courses = new ArrayList<Course>();
        long id = RAND.nextLong();

        for (final boolean published : new Boolean[] { true, false }) {
            for (final String enrollment : new String[] { ENROLLMENT_DISABLED, ENROLLMENT_OPEN, ENROLLMENT_APPROVAL,
                    ENROLLMENT_PRIVATE_TESTING, }) {
                for (final MEMBERSHIP membership : new MEMBERSHIP[] { MEMBERSHIP.NONE, MEMBERSHIP.ADMIN,
                        MEMBERSHIP.ENROLLED, MEMBERSHIP.ADMIN_ENROLLED, MEMBERSHIP.PENDING, }) {
                    for (final String guid : new String[] { null, GUID_GUEST, GUID1, GUID2 }) {
                        // generate course
                        final Course course = new Course();
                        course.setId(++id);
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
                            switch (membership) {
                            case ADMIN_ENROLLED:
                                course.addEnrolled(guid);
                            case ADMIN:
                                course.addAdmin(guid);
                                break;
                            case ENROLLED:
                                course.addEnrolled(guid);
                                break;
                            case PENDING:
                                course.addPending(guid);
                                break;
                            case NONE:
                                break;
                            }
                        }

                        courses.add(course);
                    }
                }
            }
        }

        return courses;
    }

    public static List<Video> generateVideos() {
        return generateVideos(Collections.singleton(new Client(null, RAND.nextLong())));
    }

    public static List<Video> generateVideos(final Collection<Client> clients) {
        final List<Video> videos = new ArrayList<>();
        long id = RAND.nextLong();

        for (final Client client : clients) {
            for (final String title : new String[] { null, "title", }) {
                for (final State state : EnumSet.of(State.NEW, State.ENCODING, State.CHECK,
                        State.ENCODED)) {
                    final Video video = new Video(client);
                    video.setId(++id);
                    video.setTitle(title != null ? title + "-" + Long.valueOf(id).toString() : title);
                    video.setState(state);
                    videos.add(video);
                }
            }
        }

        return videos;
    }
}
