package de.nemeses.spring.bpmn.repository.jpa;

import java.util.Collection;
import java.util.Date;
import java.util.List;

import org.springframework.transaction.support.TransactionTemplate;

import de.nemeses.spring.bpmn.repository.ProcessDefinition;
import de.nemeses.spring.bpmn.repository.ProcessDefinitionService;
import jakarta.persistence.EntityManager;

/**
 * JPA backed {@link ProcessDefinitionService}. Every registration creates a new version; nothing is overwritten.
 */
public class JpaProcessDefinitionService implements ProcessDefinitionService {

    private static final String QUERY = "select d from ProcessDefinitionEntity d where d.processName = :name "
            + "order by d.created desc, d.id desc";

    private final EntityManager entityManager;
    private final TransactionTemplate transactionTemplate;

    JpaProcessDefinitionService(final EntityManager entityManager, final TransactionTemplate transactionTemplate) {
        this.entityManager = entityManager;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * Stores the definition as a new version. A missing creation date is set to now.
     */
    @Override
    public void register(final ProcessDefinition definition) {
        final Date created = definition.createdDate() == null ? new Date() : definition.createdDate();
        final ProcessDefinitionEntity entity = new ProcessDefinitionEntity(definition.name(), created,
                definition.bpmnXml());
        this.transactionTemplate.executeWithoutResult(status -> this.entityManager.persist(entity));
    }

    /**
     * @return the version with the latest creation date, or {@code null} if no definition of that name exists
     */
    @Override
    public ProcessDefinition retrieveLatest(final String name) {
        final List<ProcessDefinitionEntity> result = this.transactionTemplate.execute(status -> this.entityManager
                .createQuery(QUERY, ProcessDefinitionEntity.class).setParameter("name", name).setMaxResults(1)
                .getResultList());
        return result == null || result.isEmpty() ? null : result.get(0);
    }

    /**
     * @return all versions, newest first; empty if none exists
     */
    @Override
    public Collection<ProcessDefinition> retrieveAll(final String name) {
        final List<ProcessDefinitionEntity> result = this.transactionTemplate.execute(status -> this.entityManager
                .createQuery(QUERY, ProcessDefinitionEntity.class).setParameter("name", name).getResultList());
        return result == null ? List.of() : List.copyOf(result);
    }
}
