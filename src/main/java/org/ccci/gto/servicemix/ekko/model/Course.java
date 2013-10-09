package org.ccci.gto.servicemix.ekko.model;

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

import org.apache.openjpa.persistence.jdbc.ContainerTable;
import org.apache.openjpa.persistence.jdbc.ForeignKey;
import org.apache.openjpa.persistence.jdbc.ForeignKeyAction;

@Entity
public class Course {
    private static final Pattern GUIDPATTERN = Pattern
            .compile("^[0-9A-F]{8}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{12}$");

    public static final String ENROLLMENT_DISABLED = "disabled";
    public static final String ENROLLMENT_OPEN = "open";
    public static final String ENROLLMENT_APPROVAL = "approval";

    @Id
    @Column(updatable = false)
    private Long id;

    private Long version = 0L;

    private String title;

    @Column(name = "public")
    private boolean publicCourse = false;

    private String enrollment = ENROLLMENT_APPROVAL;

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
    private Map<String, Resource> resources = new HashMap<String, Resource>();

    @ElementCollection(fetch = FetchType.LAZY)
    @Column(name = "guid", nullable = false, length = 36)
    @CollectionTable(name = "Course_Admins", joinColumns = @JoinColumn(name = "courseId", nullable = false), uniqueConstraints = { @UniqueConstraint(columnNames = {
            "courseId", "guid" }) })
    @ContainerTable(joinForeignKey = @ForeignKey(updateAction = ForeignKeyAction.CASCADE, deleteAction = ForeignKeyAction.CASCADE))
    private Set<String> admins = new HashSet<String>();

    @ElementCollection(fetch = FetchType.LAZY)
    @Column(name = "guid", nullable = false, length = 36)
    @CollectionTable(name = "Course_Enrolled", joinColumns = @JoinColumn(name = "courseId", nullable = false), uniqueConstraints = { @UniqueConstraint(columnNames = {
            "courseId", "guid" }) })
    @ContainerTable(joinForeignKey = @ForeignKey(updateAction = ForeignKeyAction.CASCADE, deleteAction = ForeignKeyAction.CASCADE))
    private Set<String> enrolled = new HashSet<String>();

    @Column(length = 40)
    private String zipSha1;

    public Long getId() {
        return this.id;
    }

    public Long getVersion() {
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

    public void setId(final Long id) {
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

    public Resource getResource(final String sha1) {
        if (sha1 == null) {
            return null;
        }

        return this.resources.get(sha1.toLowerCase());
    }

    public Collection<Resource> getResources() {
        return Collections.unmodifiableCollection(this.resources.values());
    }

    public String getZipSha1() {
        return this.zipSha1;
    }

    public Resource getZip() {
        return this.getResource(this.zipSha1);
    }

    public void setZip(final Resource zip) {
        if (zip == null) {
            this.zipSha1 = null;
        } else if (this.id.equals(zip.getKey().getCourseId())) {
            this.zipSha1 = zip.getSha1();
        }
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

    public boolean isPublic() {
        return this.publicCourse;
    }

    public boolean isPublished() {
        return this.manifest != null;
    }

    public boolean isEnrollment(final String type) {
        return this.enrollment.equals(type);
    }

    public boolean isAdmin(final String guid) {
        return this.admins != null && guid != null && this.admins.contains(guid.toUpperCase());
    }

    public boolean isEnrolled(final String guid) {
        return this.enrolled != null && guid != null && this.enrolled.contains(guid.toUpperCase());
    }

    public boolean canView(final String guid) {
        return this.isPublic() || this.canViewContent(guid);
    }

    public boolean canViewContent(final String guid) {
        return this.isEnrollment(ENROLLMENT_DISABLED) || this.isEnrolled(guid) || this.isAdmin(guid);
    }

    public static class CourseQuery {
        private boolean loadManifest = false;
        private boolean loadPendingManifest = false;
        private boolean loadResources = false;
        private boolean loadAdmins = false;
        private boolean loadEnrolled = false;

        private int start = 0;
        private int limit = 0;

        private Long id = null;

        private String admin = null;
        private String enrolled = null;
        private boolean publicCourse = false;
        private boolean published = false;

        public CourseQuery() {
        }

        public CourseQuery id(final Long id) {
            this.id = id;
            return this;
        }

        public CourseQuery admin(final String guid) {
            this.admin = (guid != null ? guid.toUpperCase() : null);
            return this;
        }

        public CourseQuery enrolled(final String guid) {
            this.enrolled = (guid != null ? guid.toUpperCase() : null);
            return this;
        }

        public CourseQuery publicCourse(final boolean publicCourse) {
            this.publicCourse = publicCourse;
            return this;
        }

        public CourseQuery published(final boolean published) {
            this.published = published;
            return this;
        }

        public CourseQuery loadManifest(final boolean loadManifest) {
            this.loadManifest = loadManifest;
            return this;
        }

        public CourseQuery loadPendingManifest(final boolean loadPendingManifest) {
            this.loadPendingManifest = loadPendingManifest;
            return this;
        }

        public CourseQuery loadResources(final boolean loadResources) {
            this.loadResources = loadResources;
            return this;
        }

        public CourseQuery loadAdmins(final boolean loadAdmins) {
            this.loadAdmins = loadAdmins;
            return this;
        }

        public CourseQuery loadEnrolled(final boolean loadEnrolled) {
            this.loadEnrolled = loadEnrolled;
            return this;
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
            if (this.published) {
                where.add(cb.isNotNull(c.get("manifest")));
            }

            // course permission visibility
            if (this.admin != null || this.enrolled != null || this.publicCourse) {
                final ArrayList<Predicate> visibility = new ArrayList<Predicate>();
                if (this.admin != null) {
                    visibility.add(cb.equal(c.get("admins"), cb.parameter(String.class, "adminGuid")));
                    // visibility.add(cb.parameter(String.class,
                    // "adminGuid").in(c.<Set<String>> get("admins")));
                    params.put("adminGuid", this.admin);
                }
                if (this.enrolled != null) {
                    visibility.add(cb.equal(c.get("enrolled"), cb.parameter(String.class, "enrolledGuid")));
                    // visibility.add(cb.parameter(String.class,
                    // "enrolledGuid").in(c.<Set<String>> get("enrolled")));
                    params.put("enrolledGuid", this.enrolled);
                }
                if (this.publicCourse) {
                    visibility.add(cb.equal(c.get("publicCourse"), Boolean.TRUE));
                }

                where.add(cb.or(visibility.toArray(new Predicate[visibility.size()])));
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

            newObj.admin = this.admin;
            newObj.enrolled = this.enrolled;
            newObj.publicCourse = this.publicCourse;

            newObj.loadManifest = this.loadManifest;
            newObj.loadPendingManifest = this.loadPendingManifest;
            newObj.loadResources = this.loadResources;
            newObj.loadAdmins = this.loadAdmins;
            newObj.loadEnrolled = this.loadEnrolled;
            return newObj;
        }
    }
}
