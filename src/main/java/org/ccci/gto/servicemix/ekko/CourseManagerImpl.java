package org.ccci.gto.servicemix.ekko;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;

import org.ccci.gto.servicemix.ekko.model.Course;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public class CourseManagerImpl implements CourseManager {
    @PersistenceContext
    private EntityManager em;

    @Transactional(propagation = Propagation.MANDATORY)
    @Override
    public List<Course> getCourses() {
        return this.getCourses(false);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    @Override
    public List<Course> getCourses(final boolean includeManifest) {
        final CriteriaQuery<Course> cq = this.em.getCriteriaBuilder().createQuery(Course.class);
        final Root<Course> from = cq.from(Course.class);
        if (includeManifest) {
            from.fetch("manifest");
        }
        cq.select(from);
        return this.em.createQuery(cq).getResultList();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    @Override
    public Course getCourse(final Long id) {
        return this.em.find(Course.class, id);
    }
}
