package de.nemeses.spring.bpmn.repository.jpa;

import java.util.Date;
import java.util.Map;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import de.nemeses.spring.bpmn.repository.NoSuchProcessInstanceException;
import de.nemeses.spring.bpmn.repository.ProcessDefinition;
import de.nemeses.spring.bpmn.repository.ProcessInstance;
import de.nemeses.spring.bpmn.repository.ProcessInstanceRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

/**
 * JPA backed {@link ProcessInstanceRepository}. Variables are stored as JSON. Imported by
 * {@link JpaBpmnRepositoryAutoConfiguration}.
 */
@Component
@ConditionalOnMissingBean(ProcessInstanceRepository.class)
public class JpaProcessInstanceRepository implements ProcessInstanceRepository {

    private final EntityManager entityManager;

    JpaProcessInstanceRepository(final EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * @param definition a definition retrieved from the {@link JpaProcessDefinitionService}
     * @throws IllegalArgumentException if the definition is not stored by the {@link JpaProcessDefinitionService}
     */
    @Override
    @Transactional
    public ProcessInstance create(final ProcessDefinition definition, final Map<String, Object> variables) {
        final ProcessInstanceEntity entity = new ProcessInstanceEntity(UUID.randomUUID(),
                this.definitionEntity(definition), variables == null ? Map.of() : variables, new Date());
        this.entityManager.persist(entity);
        return entity;
    }

    @Override
    @Transactional(readOnly = true)
    public ProcessInstance retrieve(final UUID id) throws NoSuchProcessInstanceException {
        return this.entity(id);
    }

    /**
     * Loads the instance with {@code SELECT ... FOR UPDATE} ({@link LockModeType#PESSIMISTIC_WRITE}); the state is
     * refreshed from the database even if the instance was already loaded in this transaction.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public ProcessInstance lock(final UUID id) throws NoSuchProcessInstanceException {
        final ProcessInstanceEntity entity = this.entity(id);
        this.entityManager.refresh(entity, LockModeType.PESSIMISTIC_WRITE);
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

    private ProcessInstanceEntity entity(final UUID id) throws NoSuchProcessInstanceException {
        final ProcessInstanceEntity entity = this.entityManager.find(ProcessInstanceEntity.class, id);
        if (entity == null) {
            throw new NoSuchProcessInstanceException(id);
        }
        return entity;
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
