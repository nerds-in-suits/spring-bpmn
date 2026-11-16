package de.nemeses.spring.bpmn.repository.jpa;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import de.nemeses.spring.bpmn.repository.NoSuchProcessInstanceException;
import de.nemeses.spring.bpmn.repository.ProcessDefinition;
import de.nemeses.spring.bpmn.repository.ProcessInstance;
import de.nemeses.spring.bpmn.repository.ProcessInstanceCount;
import de.nemeses.spring.bpmn.repository.ProcessInstancePage;
import de.nemeses.spring.bpmn.repository.ProcessInstanceRepository;
import de.nemeses.spring.bpmn.repository.ProcessInstanceSort;
import de.nemeses.spring.bpmn.repository.ProcessStep;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.TypedQuery;

/**
 * JPA backed {@link ProcessInstanceRepository}. Each variable is stored as one row with a JSON value
 * ({@link ProcessVariableEntity}). Imported by {@link JpaBpmnRepositoryAutoConfiguration}.
 */
@Component
@ConditionalOnMissingBean(ProcessInstanceRepository.class)
public class JpaProcessInstanceRepository implements ProcessInstanceRepository {

    private final EntityManager entityManager;

    JpaProcessInstanceRepository(final EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * Overridden only to be transactional (the inherited default method is not proxied).
     */
    @Override
    @Transactional
    public ProcessInstance create(final ProcessDefinition definition, final Map<String, Object> variables) {
        return this.create(definition, variables, null);
    }

    /**
     * @param definition a definition retrieved from the {@link JpaProcessDefinitionService}
     * @param parentStep a step stored by the {@link JpaProcessStepRepository}, or {@code null}
     * @throws IllegalArgumentException if the definition is not stored by the {@link JpaProcessDefinitionService} or the
     *             parent step is not stored by the {@link JpaProcessStepRepository}
     */
    @Override
    @Transactional
    public ProcessInstance create(final ProcessDefinition definition, final Map<String, Object> variables,
            final ProcessStep parentStep) {
        final ProcessInstanceEntity entity = new ProcessInstanceEntity(UUID.randomUUID(),
                this.definitionEntity(definition), variables == null ? Map.of() : variables, new Date(),
                parentStep == null ? null : this.stepEntity(parentStep));
        this.entityManager.persist(entity);
        return entity;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProcessInstance> retrieveChild(final UUID parentStepId) {
        return this.entityManager.createQuery("select i from ProcessInstanceEntity i where i.parentStepId = :stepId",
                ProcessInstance.class)
                .setParameter("stepId", parentStepId)
                .getResultStream()
                .findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public ProcessInstance retrieve(final UUID id) throws NoSuchProcessInstanceException {
        return this.entity(id);
    }

    /**
     * Locks the instance with {@code SELECT ... FOR UPDATE} ({@link LockModeType#PESSIMISTIC_WRITE}), then refreshes
     * it and its variable rows from the database, so the state is current even if the instance was already loaded in
     * this transaction. The refresh is a separate call without lock mode, because Hibernate fails to refresh with a
     * lock mode when the eagerly loaded variables use the instance as part of their primary key.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public ProcessInstance lock(final UUID id) throws NoSuchProcessInstanceException {
        final ProcessInstanceEntity entity = this.entityManager.find(ProcessInstanceEntity.class, id,
                LockModeType.PESSIMISTIC_WRITE);
        if (entity == null) {
            throw new NoSuchProcessInstanceException(id);
        }
        this.entityManager.refresh(entity);
        return entity;
    }

    @Override
    @Transactional
    public ProcessInstance updateEndDate(final UUID id, final Date endDate) throws NoSuchProcessInstanceException {
        final ProcessInstanceEntity entity = this.entity(id);
        entity.endDate(endDate);
        return entity;
    }

    @Override
    @Transactional
    public ProcessInstance updateVariables(final UUID id, final Map<String, Object> variables)
            throws NoSuchProcessInstanceException {
        final ProcessInstanceEntity entity = this.entity(id);
        entity.variables(variables);
        return entity;
    }

    /**
     * Runs one query for the page and one for the total count. Sorting by end date puts running instances (no end
     * date) last in ascending and first in descending order.
     */
    @Override
    @Transactional(readOnly = true)
    public ProcessInstancePage query(final String definitionName, final Boolean running,
            final ProcessInstanceSort sort, final boolean ascending, final int offset, final int limit) {
        final String state;
        if (running == null) {
            state = "";
        } else {
            state = running ? " and i.end is null" : " and i.end is not null";
        }
        final String where = " where 1 = 1" + (definitionName == null
                ? ""
                : " and i.definition.processDefinitionName = :name") + state;
        final String direction = ascending ? " asc" : " desc";
        final String order = sort == ProcessInstanceSort.END_DATE
                ? " order by i.end" + direction + (ascending ? " nulls last" : " nulls first")
                : " order by i.start" + direction;
        final TypedQuery<ProcessInstance> items = this.entityManager.createQuery(
                "select i from ProcessInstanceEntity i" + where + order + ", i.uuid" + direction,
                ProcessInstance.class);
        final TypedQuery<Long> total = this.entityManager.createQuery(
                "select count(i) from ProcessInstanceEntity i" + where, Long.class);
        if (definitionName != null) {
            items.setParameter("name", definitionName);
            total.setParameter("name", definitionName);
        }
        return new ProcessInstancePage(items.setFirstResult(offset).setMaxResults(limit).getResultList(),
                total.getSingleResult());
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, ProcessInstanceCount> countByDefinition() {
        final Map<String, ProcessInstanceCount> counts = new LinkedHashMap<>();
        this.entityManager.createQuery("select i.definition.processDefinitionName, count(i), count(i.end)"
                + " from ProcessInstanceEntity i group by i.definition.processDefinitionName", Object[].class)
                .getResultList()
                .forEach(row -> {
                    final long all = ((Number) row[1]).longValue();
                    final long finished = ((Number) row[2]).longValue();
                    counts.put((String) row[0], new ProcessInstanceCount(all - finished, finished));
                });
        return counts;
    }

    private ProcessInstanceEntity entity(final UUID id) throws NoSuchProcessInstanceException {
        final ProcessInstanceEntity entity = this.entityManager.find(ProcessInstanceEntity.class, id);
        if (entity == null) {
            throw new NoSuchProcessInstanceException(id);
        }
        return entity;
    }

    private ProcessStepEntity stepEntity(final ProcessStep step) {
        final ProcessStepEntity stored = this.entityManager.find(ProcessStepEntity.class, step.id());
        if (stored == null) {
            throw new IllegalArgumentException("Process step " + step.id() + " is not stored in the JPA repository.");
        }
        return stored;
    }

    private ProcessDefinitionEntity definitionEntity(final ProcessDefinition definition) {
        if (definition instanceof final ProcessDefinitionEntity entity && entity.getId() != null) {
            final ProcessDefinitionEntity stored = this.entityManager.find(ProcessDefinitionEntity.class, entity.getId());
            if (stored != null) {
                return stored;
            }
        }
        throw new IllegalArgumentException("Process definition '" + definition.name()
                + "' is not stored in the JPA repository.");
    }
}
