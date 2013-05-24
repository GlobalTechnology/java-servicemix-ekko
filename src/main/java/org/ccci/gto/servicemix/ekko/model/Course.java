package org.ccci.gto.servicemix.ekko.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

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
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.apache.openjpa.persistence.jdbc.ContainerTable;
import org.apache.openjpa.persistence.jdbc.ForeignKey;
import org.apache.openjpa.persistence.jdbc.ForeignKeyAction;

@Entity
public class Course {
    @Id
    @Column(updatable = false)
    private Long id;

    private Long version = 0L;

    private String title;

    @Column(name = "public")
    private boolean publicCourse = false;

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

    public void setPublic(boolean publicCourse) {
        this.publicCourse = publicCourse;
    }

    public void setManifest(final String manifest) {
        this.manifest = manifest;
    }

    public void setPendingManifest(final String pendingManifest) {
        this.pendingManifest = pendingManifest;
    }

    public Resource getResource(final String sha1) {
        if (sha1 == null) {
            return null;
        }

        return this.resources.get(sha1.toLowerCase());
    }

    public Collection<Resource> getResources() {
        return this.resources.values();
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

    public void addAdmin(final String guid) {
        this.admins.add(guid.toUpperCase());
    }

    public void removeAdmin(final String guid) {
        this.admins.remove(guid.toUpperCase());
    }

    public boolean isPublic() {
        return this.publicCourse;
    }

    public boolean isAdmin(final String guid) {
        return this.admins.contains(guid.toUpperCase());
    }

    public boolean isEnrolled(final String guid) {
        return this.enrolled.contains(guid.toUpperCase());
    }

    public boolean canView(final String guid) {
        return this.isPublic() || this.canViewContent(guid);
    }

    public boolean canViewContent(final String guid) {
        return this.isEnrolled(guid) || this.isAdmin(guid);
    }

    public static class CourseQuery {
        private boolean loadManifest = false;
        private boolean loadPendingManifest = false;
        private boolean loadResources = false;

        private int start = 0;
        private int limit = 0;

        private Long id = null;

        private String admin = null;

        public CourseQuery() {
        }

        public CourseQuery id(final Long id) {
            this.id = id;
            return this;
        }

        public CourseQuery admin(final String guid) {
            this.admin = guid.toUpperCase();
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

        public CourseQuery start(final int start) {
            this.start = start;
            return this;
        }

        public CourseQuery limit(final int limit) {
            this.limit = limit;
            return this;
        }

        public TypedQuery<Course> compile(final EntityManager em) {
            // generate base query
            final CriteriaBuilder cb = em.getCriteriaBuilder();
            final CriteriaQuery<Course> cq = cb.createQuery(Course.class);
            final Root<Course> c = cq.from(Course.class);
            cq.select(c);

            // should any lazy loaded fields be loaded?
            if (this.loadManifest) {
                c.fetch("manifest");
            }
            if (this.loadPendingManifest) {
                c.fetch("pendingManifest");
            }
            if (this.loadResources) {
                c.fetch("resources");
            }

            // capture various parts of query for assembly later
            final ArrayList<Predicate> where = new ArrayList<Predicate>();
            final HashMap<String, Object> params = new HashMap<String, Object>();

            // generate where clauses
            if (this.id != null) {
                where.add(cb.equal(c.get("id"), cb.parameter(Long.class, "id")));
                params.put("id", this.id);
            }
            if (this.admin != null) {
                where.add(cb.parameter(String.class, "adminGuid").in(c.get("admins")));
                params.put("adminGuid", this.admin);
            }

            // generate where clause
            if (where.size() > 0) {
                cq.where(cb.and(where.toArray(new Predicate[where.size()])));
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
            } else if (this.id != null) {
                // only return 1 result since we are selecting based on id
                query.setMaxResults(1);
            }

            // return the query
            return query;
        }
    }
}
