package de.nemeses.spring.bpmn.repository.jpa;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import de.nemeses.spring.bpmn.repository.NoSuchProcessInstanceException;
import de.nemeses.spring.bpmn.repository.NoSuchProcessStepException;
import de.nemeses.spring.bpmn.repository.ProcessStep;
import de.nemeses.spring.bpmn.repository.ProcessStepRepository;
import jakarta.persistence.EntityManager;

/**
 * JPA backed {@link ProcessStepRepository} (table {@code spring_bpmn_process_step}). Pending steps are always queried
 * from the database, so steps completed by other, committed transactions are not returned. Imported by
 * {@link JpaBpmnRepositoryAutoConfiguration}.
 */
@Component
@ConditionalOnMissingBean(ProcessStepRepository.class)
public class JpaProcessStepRepository implements ProcessStepRepository {

    private final EntityManager entityManager;

    JpaProcessStepRepository(final EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * Overridden only to be transactional (the inherited default method is not proxied).
     */
    @Override
    @Transactional
    public ProcessStep create(final UUID instanceId, final String nodeId, final String incomingFlowId,
            final UUID parentStepId) throws NoSuchProcessInstanceException {
        return this.create(instanceId, nodeId, incomingFlowId, parentStepId, null, null);
    }

    @Override
    @Transactional
    public ProcessStep create(final UUID instanceId, final String nodeId, final String incomingFlowId,
            final UUID parentStepId, final Integer loopCounter, final Object loopItem)
            throws NoSuchProcessInstanceException {
        final ProcessInstanceEntity instance = this.entityManager.find(ProcessInstanceEntity.class, instanceId);
        if (instance == null) {
            throw new NoSuchProcessInstanceException(instanceId);
        }
        final ProcessStepEntity entity = new ProcessStepEntity(UUID.randomUUID(), instance, nodeId, incomingFlowId,
                parentStepId, new Date());
        entity.loop(loopCounter, loopItem);
        this.entityManager.persist(entity);
        return entity;
    }

    @Override
    @Transactional(readOnly = true)
    public ProcessStep retrieve(final UUID id) throws NoSuchProcessStepException {
        return this.entity(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProcessStep> pending(final UUID instanceId, final String nodeId) {
        return List.copyOf(this.entityManager.createQuery("select s from ProcessStepEntity s"
                + " where s.instanceId = :instanceId and s.node = :nodeId and s.end is null order by s.start",
                ProcessStepEntity.class)
                .setParameter("instanceId", instanceId)
                .setParameter("nodeId", nodeId)
                .getResultList());
    }

    @Override
    @Transactional(readOnly = true)
    public Collection<ProcessStep> children(final UUID parentStepId) {
        return List.copyOf(this.entityManager.createQuery("select s from ProcessStepEntity s"
                + " where s.parentStep = :parentStepId order by s.start", ProcessStepEntity.class)
                .setParameter("parentStepId", parentStepId)
                .getResultList());
    }

    @Override
    @Transactional(readOnly = true)
    public long countPending(final UUID instanceId) {
        return this.entityManager.createQuery("select count(s) from ProcessStepEntity s"
                + " where s.instanceId = :instanceId and s.end is null", Long.class)
                .setParameter("instanceId", instanceId)
                .getSingleResult();
    }

    @Override
    @Transactional(rollbackFor = NoSuchProcessStepException.class)
    public void complete(final Collection<UUID> ids, final Date endDate) throws NoSuchProcessStepException {
        for (final UUID id : ids) {
            this.entity(id).endDate(endDate);
        }
    }

    @Override
    @Transactional(rollbackFor = NoSuchProcessStepException.class)
    public void updateLoop(final UUID id, final Integer loopCounter, final Object loopItem)
            throws NoSuchProcessStepException {
        this.entity(id).loop(loopCounter, loopItem);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProcessStep> steps(final UUID instanceId) {
        return List.copyOf(this.entityManager.createQuery("select s from ProcessStepEntity s"
                + " where s.instanceId = :instanceId order by s.start", ProcessStepEntity.class)
                .setParameter("instanceId", instanceId)
                .getResultList());
    }

    private ProcessStepEntity entity(final UUID id) throws NoSuchProcessStepException {
        final ProcessStepEntity entity = this.entityManager.find(ProcessStepEntity.class, id);
        if (entity == null) {
            throw new NoSuchProcessStepException(id);
        }
        return entity;
    }
}
