package org.ccci.gto.servicemix.ekko.model;

import static org.ccci.gto.servicemix.ekko.Constants.GUID_GUEST;

import org.apache.openjpa.persistence.jdbc.ContainerTable;
import org.apache.openjpa.persistence.jdbc.ForeignKey;
import org.apache.openjpa.persistence.jdbc.ForeignKeyAction;

import javax.persistence.Basic;
import javax.persistence.CascadeType;
import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.Lob;
import javax.persistence.MapKeyColumn;
import javax.persistence.OneToMany;
import javax.persistence.TypedQuery;
import javax.persistence.UniqueConstraint;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Pattern;

@Entity
public class Course {
    private static final Pattern GUIDPATTERN = Pattern
            .compile("^[0-9A-F]{8}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{12}$");

    public static final String ENROLLMENT_DISABLED = "disabled";
    public static final String ENROLLMENT_OPEN = "open";
    public static final String ENROLLMENT_APPROVAL = "approval";

    @Id
    @Column(updatable = false)
    private long id = 0;

    private long version = 0;

    private String title;

    @Column(name = "public")
    private boolean publicCourse = true;

    @Column(length = 20, nullable = false)
    private String enrollment = ENROLLMENT_OPEN;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(length = 1000000)
    private String manifest;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(length = 1000000)
    private String pendingManifest;

    @OneToMany(mappedBy = "course", fetch = FetchType.LAZY, cascade = CascadeType.REMOVE)
    @MapKeyColumn(name = "sha1")
    private Map<String, FileResource> resources = new HashMap<>();

    @OneToMany(mappedBy = "course", fetch = FetchType.LAZY, cascade = CascadeType.REMOVE)
    @MapKeyColumn(name = "videoId")
    private Map<Long, VideoResource> videoResources = new HashMap<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @Column(name = "guid", nullable = false, length = 36)
    @CollectionTable(name = "Course_Admins", joinColumns = @JoinColumn(name = "courseId", nullable = false), uniqueConstraints = { @UniqueConstraint(columnNames = {
            "courseId", "guid" }) })
    @ContainerTable(joinForeignKey = @ForeignKey(updateAction = ForeignKeyAction.CASCADE, deleteAction = ForeignKeyAction.CASCADE))
    private Set<String> admins = new HashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @Column(name = "guid", nullable = false, length = 36)
    @CollectionTable(name = "Course_Enrolled", joinColumns = @JoinColumn(name = "courseId", nullable = false), uniqueConstraints = { @UniqueConstraint(columnNames = {
            "courseId", "guid" }) })
    @ContainerTable(joinForeignKey = @ForeignKey(updateAction = ForeignKeyAction.CASCADE, deleteAction = ForeignKeyAction.CASCADE))
    private Set<String> enrolled = new HashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @Column(name = "guid", nullable = false, length = 36)
    @CollectionTable(name = "Course_Pending", joinColumns = @JoinColumn(name = "courseId", nullable = false), uniqueConstraints = { @UniqueConstraint(columnNames = {
            "courseId", "guid" }) })
    @ContainerTable(joinForeignKey = @ForeignKey(updateAction = ForeignKeyAction.CASCADE, deleteAction = ForeignKeyAction.CASCADE))
    private Set<String> pending = new HashSet<>();

    public long getId() {
        return this.id;
    }

    public long getVersion() {
        return this.version;
    }

    public String getTitle() {
        return this.title;
    }

    public String getManifest() {
        return this.manifest;
    }

    public String getPendingManifest() {
        return this.pendingManifest;
    }

    public void setId(final long id) {
        this.id = id;
    }

    public void incVersion() {
        this.version++;
    }

    public void setTitle(final String title) {
        this.title = title;
    }

    public void setPublic(final boolean publicCourse) {
        if (!publicCourse) {
            // when making this a private course, we need to use approval
            // enrollment
            this.enrollment = ENROLLMENT_APPROVAL;
        }
        this.publicCourse = publicCourse;
    }

    public void setManifest(final String manifest) {
        this.manifest = manifest;
    }

    public void setPendingManifest(final String pendingManifest) {
        this.pendingManifest = pendingManifest;
    }

    public final String getEnrollment() {
        return this.enrollment;
    }

    public final void setEnrollment(final String enrollment) {
        // only set enrollment if it matches the supported types
        switch (enrollment) {
        case ENROLLMENT_DISABLED:
        case ENROLLMENT_OPEN:
            // when disabling or using open enrollment, the course needs to be
            // public
            this.publicCourse = true;
        case ENROLLMENT_APPROVAL:
            this.enrollment = enrollment;
            break;
        default:
            throw new IllegalArgumentException("invalid enrollment type: " + enrollment);
        }
    }

    public FileResource getResource(final String sha1) {
        if (sha1 == null) {
            return null;
        }

        return this.resources != null ? this.resources.get(sha1.toLowerCase()) : null;
    }

    public Collection<FileResource> getResources() {
        return Collections.unmodifiableCollection(this.resources.values());
    }

    public VideoResource getVideoResource(final long videoId) {
        return this.videoResources != null ? this.videoResources.get(videoId) : null;
    }

    public Collection<VideoResource> getVideoResources() {
        return Collections.unmodifiableCollection(this.videoResources.values());
    }

    public void addAdmin(String guid) {
        if (guid != null) {
            guid = guid.toUpperCase();

            // only add valid GUIDs
            if (GUIDPATTERN.matcher(guid).matches()) {
                if (this.admins == null) {
                    this.admins = new HashSet<String>();
                }

                this.admins.add(guid.toUpperCase());
            }
        }
    }

    public void addAdmins(final Collection<String> guids) {
        if (guids != null) {
            for (final String guid : guids) {
                this.addAdmin(guid);
            }
        }
    }

    public void removeAdmin(final String guid) {
        if (guid != null && this.admins != null) {
            this.admins.remove(guid.toUpperCase());
        }
    }

    public void removeAdmins(final Collection<String> guids) {
        if (guids != null && this.admins != null) {
            for (final String guid : guids) {
                this.removeAdmin(guid);
            }
        }
    }

    public void setAdmins(final Collection<String> guids) {
        if (this.admins != null) {
            this.admins.clear();
        }
        this.addAdmins(guids);
    }

    public Set<String> getAdmins() {
        if (this.admins == null) {
            return Collections.emptySet();
        }

        return Collections.unmodifiableSet(this.admins);
    }

    public void addEnrolled(String guid) {
        if (guid != null) {
            guid = guid.toUpperCase();

            // only add valid GUIDs
            if (GUIDPATTERN.matcher(guid).matches()) {
                if (this.enrolled == null) {
                    this.enrolled = new HashSet<String>();
                }

                this.enrolled.add(guid.toUpperCase());
            }
        }
    }

    public void addEnrolled(final Collection<String> guids) {
        if (guids != null) {
            for (final String guid : guids) {
                this.addEnrolled(guid);
            }
        }
    }

    public void removeEnrolled(final String guid) {
        if (guid != null && this.enrolled != null) {
            this.enrolled.remove(guid.toUpperCase());
        }
    }

    public void removeEnrolled(final Collection<String> guids) {
        if (guids != null && this.enrolled != null) {
            for (final String guid : guids) {
                this.removeEnrolled(guid);
            }
        }
    }

    public void setEnrolled(final Collection<String> guids) {
        if (this.enrolled != null) {
            this.enrolled.clear();
        }
        this.addEnrolled(guids);
    }

    public Set<String> getEnrolled() {
        if (this.enrolled == null) {
            return Collections.emptySet();
        }

        return Collections.unmodifiableSet(this.enrolled);
    }

    public void addPending(String guid) {
        if (guid != null) {
            guid = guid.toUpperCase();

            // only add valid GUIDs
            if (GUIDPATTERN.matcher(guid).matches()) {
                if (this.pending == null) {
                    this.pending = new HashSet<String>();
                }

                this.pending.add(guid.toUpperCase());
            }
        }
    }

    public void addPending(final Collection<String> guids) {
        if (guids != null) {
            for (final String guid : guids) {
                this.addPending(guid);
            }
        }
    }

    public void setPending(final Collection<String> guids) {
        if (this.pending != null) {
            this.pending.clear();
        }
        this.addPending(guids);
    }

    public void removePending(final String guid) {
        if (guid != null && this.pending != null) {
            this.pending.remove(guid.toUpperCase());
        }
    }

    public void removePending(final Collection<String> guids) {
        if (guids != null && this.pending != null) {
            for (final String guid : guids) {
                this.removePending(guid);
            }
        }
    }

    public Set<String> getPending() {
        if (this.pending == null) {
            return Collections.emptySet();
        }

        return Collections.unmodifiableSet(this.pending);
    }

    public boolean isPublic() {
        return this.publicCourse;
    }

    public boolean isPublished() {
        return this.manifest != null;
    }

    public boolean isEnrollment(final String type) {
        return this.enrollment != null && this.enrollment.equals(type);
    }

    public boolean isAdmin(final String guid) {
        return this.admins != null && guid != null && this.admins.contains(guid.toUpperCase());
    }

    public boolean isEnrolled(final String guid) {
        return this.enrolled != null && guid != null && this.enrolled.contains(guid.toUpperCase());
    }

    public boolean isPending(final String guid) {
        return this.pending != null && guid != null && this.pending.contains(guid.toUpperCase());
    }

    public boolean isContentVisibleTo(final String guid) {
        // there is no need to check if this is the GUEST guid, isEnrolled and
        // isAdmin will return false because a GUEST cannot be in those lists
        // and we want to allow access for enrollment disabled courses
        return this.isPublished()
                && (this.isEnrollment(ENROLLMENT_DISABLED) || this.isEnrolled(guid) || this.isAdmin(guid));
    }

    public boolean isVisibleTo(final String guid) {
        if (this.isPublished()) {
            if (GUID_GUEST.equals(guid)) {
                return this.isEnrollment(ENROLLMENT_DISABLED);
            } else {
                return this.isPublic() || this.isContentVisibleTo(guid);
            }
        }

        return false;
    }

    public static class CourseQuery {
        // additional data that should be loaded
        private boolean loadManifest = false;
        private boolean loadPendingManifest = false;
        private boolean loadResources = false;
        private boolean loadAdmins = false;
        private boolean loadEnrolled = false;
        private boolean loadPending = false;

        // where parameters (they are and-ed together)
        private Long id = null;
        private boolean published = false;

        // visibility attributes (they are exclusive options)
        private String adminGuid = null;
        private String enrolledGuid = null;
        private String pendingGuid = null;
        private String contentVisibleGuid = null;
        private String visibleGuid = null;

        // limit values
        private int start = 0;
        private int limit = 0;

        public CourseQuery() {
        }

        public CourseQuery id(final Long id) {
            this.id = id;
            return this;
        }

        public CourseQuery admin(final String guid) {
            if (guid != null) {
                this.adminGuid = guid.toUpperCase();
                this.contentVisibleGuid = null;
                this.visibleGuid = null;
            } else {
                this.adminGuid = null;
            }

            return this;
        }

        public CourseQuery enrolled(final String guid) {
            if (guid != null) {
                this.enrolledGuid = guid.toUpperCase();
                this.contentVisibleGuid = null;
                this.visibleGuid = null;
            } else {
                this.enrolledGuid = null;
            }

            return this;
        }

        public CourseQuery pending(final String guid) {
            if (guid != null) {
                this.pendingGuid = guid.toUpperCase();
                this.contentVisibleGuid = null;
                this.visibleGuid = null;
            } else {
                this.pendingGuid = null;
            }

            return this;
        }

        public CourseQuery contentVisibleTo(final String guid) {
            if (guid != null) {
                this.contentVisibleGuid = guid.toUpperCase();
                this.adminGuid = null;
                this.enrolledGuid = null;
                this.pendingGuid = null;
                this.visibleGuid = null;
            } else {
                this.contentVisibleGuid = null;
            }

            return this;
        }

        public CourseQuery visibleTo(final String guid) {
            if (guid != null) {
                this.visibleGuid = guid.toUpperCase();
                this.adminGuid = null;
                this.contentVisibleGuid = null;
                this.enrolledGuid = null;
                this.pendingGuid = null;
            } else {
                this.visibleGuid = null;
            }

            return this;
        }

        public CourseQuery published() {
            return this.published(true);
        }

        public CourseQuery published(final boolean published) {
            this.published = published;
            return this;
        }

        public CourseQuery loadManifest() {
            return this.loadManifest(true);
        }

        public CourseQuery loadManifest(final boolean loadManifest) {
            this.loadManifest = loadManifest;
            return this;
        }

        public CourseQuery loadPendingManifest() {
            return this.loadPendingManifest(true);
        }

        public CourseQuery loadPendingManifest(final boolean loadPendingManifest) {
            this.loadPendingManifest = loadPendingManifest;
            return this;
        }

        public CourseQuery loadResources() {
            return this.loadResources(true);
        }

        public CourseQuery loadResources(final boolean loadResources) {
            this.loadResources = loadResources;
            return this;
        }

        public CourseQuery loadAdmins() {
            return this.loadAdmins(true);
        }

        public CourseQuery loadAdmins(final boolean loadAdmins) {
            this.loadAdmins = loadAdmins;
            return this;
        }

        public CourseQuery loadEnrolled() {
            return this.loadEnrolled(true);
        }

        public CourseQuery loadEnrolled(final boolean loadEnrolled) {
            this.loadEnrolled = loadEnrolled;
            return this;
        }

        public CourseQuery loadPending() {
            return this.loadPending(true);
        }

        public CourseQuery loadPending(final boolean loadPending) {
            this.loadPending = loadPending;
            return this;
        }

        public CourseQuery loadPermissionAttrs() {
            return this.loadAdmins().loadEnrolled().loadPending();
        }

        public CourseQuery start(final int start) {
            this.start = start;
            return this;
        }

        public CourseQuery limit(final int limit) {
            this.limit = limit;
            return this;
        }

        private TypedQuery<Course> compile(final EntityManager em) {
            // generate base query
            final CriteriaBuilder cb = em.getCriteriaBuilder();
            final CriteriaQuery<Course> cq = cb.createQuery(Course.class);
            final Root<Course> c = cq.from(Course.class);
            cq.select(c);
            cq.distinct(true);

            // should any lazy loaded fields be loaded?
            if (this.loadManifest) {
                c.fetch("manifest");
            }
            if (this.loadPendingManifest) {
                c.fetch("pendingManifest");
            }
            if (this.loadAdmins) {
                c.fetch("admins", JoinType.LEFT);
            }
            if (this.loadEnrolled) {
                c.fetch("enrolled", JoinType.LEFT);
            }
            if (this.loadPending) {
                c.fetch("pending", JoinType.LEFT);
            }
            if (this.loadResources) {
                c.fetch("resources", JoinType.LEFT);
            }

            // capture various parts of query for assembly later
            final ArrayList<Predicate> where = new ArrayList<Predicate>();
            final HashMap<String, Object> params = new HashMap<String, Object>();

            // generate where clauses
            if (this.id != null) {
                where.add(cb.equal(c.get("id"), cb.parameter(Long.class, "id")));
                params.put("id", this.id);
            }

            // visibleGuid or contentVisibleGuid implies it's a published course
            if (this.published || this.visibleGuid != null || this.contentVisibleGuid != null) {
                where.add(cb.isNotNull(c.get("manifest")));
            }

            // visibility filters, these should be exclusive (we check most
            // restrictive first)
            if (this.adminGuid != null || this.enrolledGuid != null || this.pendingGuid != null) {
                final List<Predicate> filters = new ArrayList<Predicate>();

                if (this.adminGuid != null) {
                    filters.add(cb.equal(c.get("admins"), cb.parameter(String.class, "adminGuid")));
                    params.put("adminGuid", this.adminGuid);
                }
                if (this.enrolledGuid != null) {
                    filters.add(cb.equal(c.get("enrolled"), cb.parameter(String.class, "enrolledGuid")));
                    params.put("enrolledGuid", this.enrolledGuid);
                }
                if (this.pendingGuid != null) {
                    filters.add(cb.equal(c.get("pending"), cb.parameter(String.class, "pendingGuid")));
                    params.put("pendingGuid", this.pendingGuid);
                }

                where.add(cb.or(filters.toArray(new Predicate[filters.size()])));
            } else if (this.contentVisibleGuid != null) {
                where.add(cb.or(cb.equal(c.get("enrollment"), ENROLLMENT_DISABLED),
                        cb.equal(c.get("enrolled"), cb.parameter(String.class, "enrolledGuid")),
                        cb.equal(c.get("admins"), cb.parameter(String.class, "adminGuid"))));
                params.put("enrolledGuid", this.contentVisibleGuid);
                params.put("adminGuid", this.contentVisibleGuid);
            } else if (this.visibleGuid != null) {
                if (this.visibleGuid.equals(GUID_GUEST)) {
                    where.add(cb.equal(c.get("publicCourse"), Boolean.TRUE));
                    where.add(cb.equal(c.get("enrollment"), ENROLLMENT_DISABLED));
                } else {
                    where.add(cb.or(cb.equal(c.get("enrollment"), ENROLLMENT_DISABLED),
                            cb.equal(c.get("publicCourse"), Boolean.TRUE),
                            cb.equal(c.get("enrolled"), cb.parameter(String.class, "enrolledGuid")),
                            cb.equal(c.get("admins"), cb.parameter(String.class, "adminGuid"))));
                    params.put("enrolledGuid", this.visibleGuid);
                    params.put("adminGuid", this.visibleGuid);
                }
            }

            // generate where clause
            if (where.size() > 1) {
                cq.where(where.toArray(new Predicate[where.size()]));
            } else if (where.size() == 1) {
                cq.where(where.get(0));
            }

            // compile query
            final TypedQuery<Course> query = em.createQuery(cq);

            // bind parameters
            for (final Entry<String, Object> entry : params.entrySet()) {
                query.setParameter(entry.getKey(), entry.getValue());
            }

            // set limits for this query
            query.setFirstResult(this.start);
            if (this.limit > 0) {
                query.setMaxResults(this.limit);
            }

            // return the query
            return query;
        }

        public List<Course> execute(final EntityManager em) {
            return this.compile(em).getResultList();
        }

        @Override
        public CourseQuery clone() {
            final CourseQuery newObj = new CourseQuery();
            newObj.id = this.id;
            newObj.start = this.start;
            newObj.limit = this.limit;

            newObj.adminGuid = this.adminGuid;
            newObj.enrolledGuid = this.enrolledGuid;
            newObj.pendingGuid = this.pendingGuid;
            newObj.published = this.published;
            newObj.visibleGuid = this.visibleGuid;
            newObj.contentVisibleGuid = this.contentVisibleGuid;

            newObj.loadManifest = this.loadManifest;
            newObj.loadPendingManifest = this.loadPendingManifest;
            newObj.loadResources = this.loadResources;
            newObj.loadAdmins = this.loadAdmins;
            newObj.loadEnrolled = this.loadEnrolled;
            return newObj;
        }
    }
}
