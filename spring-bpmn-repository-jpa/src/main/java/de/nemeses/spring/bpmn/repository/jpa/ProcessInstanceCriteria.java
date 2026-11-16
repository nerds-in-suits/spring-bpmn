package de.nemeses.spring.bpmn.repository.jpa;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

import de.nemeses.spring.bpmn.repository.ProcessInstanceRepository;
import de.nemeses.spring.bpmn.repository.ProcessInstanceSort;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Nulls;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

/**
 * Criteria queries of {@link ProcessInstanceRepository#query}: both the page and the total count use the same filter.
 */
final class ProcessInstanceCriteria {

    private ProcessInstanceCriteria() {
        // static helpers only
    }

    /**
     * Sorting by end date puts running instances (no end date) last in ascending and first in descending order.
     * Instances with the same date are ordered by id.
     *
     * @param builder the criteria builder of the entity manager
     * @param definitionName only instances of this definition name, {@code null} for all
     * @param running only running ({@code true}) or finished ({@code false}) instances, {@code null} for both
     * @param sort the sort order
     * @param ascending the sort direction
     * @return the query of the matching instances in the requested order
     */
    static CriteriaQuery<ProcessInstanceEntity> items(final CriteriaBuilder builder, final String definitionName,
            final Boolean running, final ProcessInstanceSort sort, final boolean ascending) {
        final CriteriaQuery<ProcessInstanceEntity> query = builder.createQuery(ProcessInstanceEntity.class);
        final Root<ProcessInstanceEntity> instance = query.from(ProcessInstanceEntity.class);
        final Order byDate = sort == ProcessInstanceSort.END_DATE
                ? ProcessInstanceCriteria.order(builder, instance.get("end"), ascending,
                        ascending ? Nulls.LAST : Nulls.FIRST)
                : ProcessInstanceCriteria.order(builder, instance.get("start"), ascending, Nulls.NONE);
        final Order byId = ProcessInstanceCriteria.order(builder, instance.get("uuid"), ascending, Nulls.NONE);
        return query.select(instance)
                .where(ProcessInstanceCriteria.filter(builder, instance, definitionName, running))
                .orderBy(byDate, byId);
    }

    /**
     * @param builder the criteria builder of the entity manager
     * @param definitionName only instances of this definition name, {@code null} for all
     * @param running only running ({@code true}) or finished ({@code false}) instances, {@code null} for both
     * @return the query of the number of matching instances
     */
    static CriteriaQuery<Long> total(final CriteriaBuilder builder, final String definitionName,
            final Boolean running) {
        final CriteriaQuery<Long> query = builder.createQuery(Long.class);
        final Root<ProcessInstanceEntity> instance = query.from(ProcessInstanceEntity.class);
        return query.select(builder.count(instance))
                .where(ProcessInstanceCriteria.filter(builder, instance, definitionName, running));
    }

    private static Predicate[] filter(final CriteriaBuilder builder, final Root<ProcessInstanceEntity> instance,
            final String definitionName, final Boolean running) {
        final Collection<Predicate> conditions = new ArrayList<>();
        if (definitionName != null) {
            conditions.add(builder.equal(instance.get("definition").get("processDefinitionName"), definitionName));
        }
        if (running != null) {
            final Path<Date> end = instance.get("end");
            conditions.add(running ? builder.isNull(end) : builder.isNotNull(end));
        }
        return conditions.toArray(Predicate[]::new);
    }

    private static Order order(final CriteriaBuilder builder, final Path<?> path, final boolean ascending,
            final Nulls nulls) {
        return ascending ? builder.asc(path, nulls) : builder.desc(path, nulls);
    }
}
