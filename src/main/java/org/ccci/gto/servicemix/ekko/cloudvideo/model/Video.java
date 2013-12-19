package org.ccci.gto.servicemix.ekko.cloudvideo.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import javax.persistence.AttributeOverride;
import javax.persistence.AttributeOverrides;
import javax.persistence.CascadeType;
import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.NoResultException;
import javax.persistence.NonUniqueResultException;
import javax.persistence.OneToMany;
import javax.persistence.TypedQuery;
import javax.persistence.UniqueConstraint;
import javax.persistence.Version;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.apache.openjpa.persistence.jdbc.Index;
import org.ccci.gto.persistence.FoundRowsList;
import org.ccci.gto.servicemix.common.model.Client;
import org.ccci.gto.servicemix.ekko.cloudvideo.model.AwsOutput.Type;

@Entity
@NamedQueries({
        @NamedQuery(name = "Video.findByState", query = "SELECT DISTINCT v FROM Video v WHERE v.state = :state"),
        @NamedQuery(name = "Video.findByJobId", query = "SELECT DISTINCT v FROM Video v JOIN v.jobs j WHERE j.id = :jobId"),
        @NamedQuery(name = "Video.findByOldJobs", query = "SELECT DISTINCT v FROM Video v JOIN v.jobs j WHERE j.lastChecked < :date"), })
public class Video {
    public enum State {
        NEW, NEW_MASTER, ENCODING, CHECK, ENCODED
    }

    @Id
    @Column(updatable = false)
    private long id = 0;

    @Version
    @Column(nullable = false)
    private int version = 0;

    @Index
    @Column(name = "client_id", nullable = false)
    private long clientId = -1;

    @Column(name = "grouping")
    private String grouping;

    @Column(nullable = false)
    private boolean locked = false;

    @Column(nullable = false)
    private long lockTimestamp = 0L;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private State state = State.NEW;

    @Column(nullable = false)
    private String title = "";

    @CollectionTable(name = "Video_jobs", joinColumns = @JoinColumn(name = "videoId", referencedColumnName = "id"), uniqueConstraints = @UniqueConstraint(columnNames = {
            "videoId", "jobId" }))
    @ElementCollection(fetch = FetchType.LAZY)
    private List<AwsJob> jobs = new ArrayList<>();

    @AttributeOverrides({ @AttributeOverride(name = "bucket", column = @Column(name = "master_awsBucket")),
            @AttributeOverride(name = "key", column = @Column(name = "master_awsKey")), })
    @Embedded
    private AwsFile master = null;

    @AttributeOverrides({ @AttributeOverride(name = "bucket", column = @Column(name = "thumbnail_awsBucket")),
            @AttributeOverride(name = "key", column = @Column(name = "thumbnail_awsKey")), })
    @Embedded
    private AwsFile thumbnail = null;

    @Column(nullable = false)
    private boolean staleThumbnail = false;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "video", cascade = CascadeType.REMOVE)
    private List<AwsOutput> outputs;

    public Video() {
    }

    public Video(final Client client) {
        this.clientId = client.getId();
    }

    public final long getId() {
        return this.id;
    }

    public final void setId(final long id) {
        this.id = id;
    }

    public final long getVersion() {
        return version;
    }

    public final long getClientId() {
        return this.clientId;
    }

    public void setState(final State state) {
        this.state = state;
    }

    public State getState() {
        return this.state;
    }

    public boolean isInState(final State state) {
        return this.state == state;
    }

    public final String getTitle() {
        return this.title;
    }

    public final void setTitle(final String title) {
        this.title = title != null ? title : "";
    }

    public final String getGrouping() {
        return this.grouping;
    }

    public final void setGrouping(final String grouping) {
        this.grouping = grouping;
    }

    public void addJob(final String jobId) {
        this.addJob(new AwsJob(jobId));
    }

    public void addJob(final AwsJob job) {
        if (this.jobs == null) {
            this.jobs = new ArrayList<>();
        }
        this.jobs.add(job);
    }

    public final List<AwsJob> getJobs() {
        return this.jobs != null ? Collections.unmodifiableList(this.jobs) : Collections.<AwsJob> emptyList();
    }

    public final AwsJob getJob(final String jobId) {
        if (this.jobs != null && jobId != null) {
            for (final AwsJob job : this.jobs) {
                if (jobId.equals(job.getId())) {
                    return job;
                }
            }
        }

        return null;
    }

    public final void removeJob(final AwsJob job) {
        if (job != null) {
            this.removeJob(job.getId());
        }
    }

    public final void removeJob(final String job) {
        if (this.jobs != null && job != null) {
            for (int i = 0; i < this.jobs.size(); i++) {
                if (job.equals(this.jobs.get(i).getId())) {
                    this.jobs.remove(i);
                    break;
                }
            }
        }
    }

    public AwsFile getMaster() {
        return this.master;
    }

    public void setMaster(final AwsFile master) {
        this.master = master;
    }

    public final boolean isStaleThumbnail() {
        return this.staleThumbnail;
    }

    public final void setStaleThumbnail(final boolean staleThumbnail) {
        this.staleThumbnail = staleThumbnail;
    }

    public AwsFile getThumbnail() {
        return this.thumbnail;
    }

    public void setThumbnail(final AwsFile thumbnail) {
        this.thumbnail = thumbnail;
    }

    public final AwsOutput getOutput(final Type type) {
        if (this.outputs != null) {
            for (final AwsOutput output : this.outputs) {
                if (output.getType() == type) {
                    return output;
                }
            }
        }

        return null;
    }

    public final List<AwsOutput> getOutputs() {
        if (this.outputs == null) {
            this.outputs = new ArrayList<>();
        }

        return this.outputs;
    }

    public boolean getLock() {
        if (this.locked) {
            return false;
        }

        this.locked = true;
        this.lockTimestamp = System.currentTimeMillis();
        return true;
    }

    public void releaseLock() {
        this.locked = false;
    }

    public static class VideoQuery {
        // limit values
        private int start = 0;
        private int limit = 0;

        // where parameters (they are and-ed together)
        private Long id = null;
        private Long clientId = null;
        private String grouping = null;

        // flags
        private boolean calcFoundRows = false;

        public VideoQuery start(final int start) {
            this.start = start;
            return this;
        }

        public VideoQuery limit(final int limit) {
            this.limit = limit;
            return this;
        }

        public VideoQuery id(final Long id) {
            this.id = id;
            return this;
        }

        public VideoQuery client(final Client client) {
            this.clientId = client != null ? client.getId() : null;
            return this;
        }

        public VideoQuery grouping(final String grouping) {
            this.grouping = grouping;
            return this;
        }

        public VideoQuery calcFoundRows(final boolean enabled) {
            this.calcFoundRows = enabled;
            return this;
        }

        private HashMap<String, Object> whereClause(final CriteriaBuilder cb, final CriteriaQuery<?> cq,
                final Root<Video> v) {
            final ArrayList<Predicate> where = new ArrayList<Predicate>();
            final HashMap<String, Object> params = new HashMap<String, Object>();

            // generate where predicates
            if (this.id != null) {
                where.add(cb.equal(v.get("id"), cb.parameter(Long.class, "id")));
                params.put("id", this.id);
            }
            if (this.clientId != null) {
                where.add(cb.equal(v.get("clientId"), cb.parameter(Long.class, "client_id")));
                params.put("client_id", this.clientId);
            }
            if (this.grouping != null) {
                where.add(cb.equal(v.get("grouping"), cb.parameter(String.class, "grouping")));
                params.put("grouping", this.grouping);
            }

            // attach where clause
            if (where.size() > 1) {
                cq.where(where.toArray(new Predicate[where.size()]));
            } else if (where.size() == 1) {
                cq.where(where.get(0));
            }

            // return any params to bind
            return params;
        }

        private void bindParams(final TypedQuery<?> query, final HashMap<String, Object> params) {
            // bind parameters
            for (final Entry<String, Object> entry : params.entrySet()) {
                query.setParameter(entry.getKey(), entry.getValue());
            }
        }

        private TypedQuery<Video> compile(final EntityManager em) {
            // generate base query
            final CriteriaBuilder cb = em.getCriteriaBuilder();
            final CriteriaQuery<Video> cq = cb.createQuery(Video.class);
            final Root<Video> v = cq.from(Video.class);
            cq.select(v);
            cq.distinct(true);

            // generate where clauses
            final HashMap<String, Object> params = this.whereClause(cb, cq, v);

            // compile query & bind params
            final TypedQuery<Video> query = em.createQuery(cq);
            this.bindParams(query, params);

            // set limits for this query
            query.setFirstResult(this.start);
            if (this.limit > 0) {
                query.setMaxResults(this.limit);
            }

            // return the query
            return query;
        }

        private TypedQuery<Long> compileFoundRows(final EntityManager em) {
            // generate base query
            final CriteriaBuilder cb = em.getCriteriaBuilder();
            final CriteriaQuery<Long> cq = cb.createQuery(Long.class);
            final Root<Video> v = cq.from(Video.class);
            cq.select(cb.countDistinct(v));

            // generate where clauses
            final HashMap<String, Object> params = this.whereClause(cb, cq, v);

            // compile query & bind params
            final TypedQuery<Long> query = em.createQuery(cq);
            this.bindParams(query, params);

            // return the query
            return query;
        }

        public List<Video> execute(final EntityManager em) {
            final List<Video> results = this.compile(em).getResultList();
            final int count = results.size();
            if ((this.start == 0 || count > 0) && (this.limit == 0 || count < this.limit)) {
                return new FoundRowsList<Video>(results, this.start + count);
            } else if (this.calcFoundRows) {
                try {
                    return new FoundRowsList<Video>(results, this.compileFoundRows(em).getSingleResult());
                } catch (final NoResultException | NonUniqueResultException ignored) {
                }
            }

            return results;
        }

        @Override
        public VideoQuery clone() {
            final VideoQuery newObj = new VideoQuery();
            newObj.start = this.start;
            newObj.limit = this.limit;

            newObj.id = this.id;
            newObj.clientId = this.clientId;
            newObj.grouping = this.grouping;

            newObj.calcFoundRows = this.calcFoundRows;

            return newObj;
        }
    }
}
