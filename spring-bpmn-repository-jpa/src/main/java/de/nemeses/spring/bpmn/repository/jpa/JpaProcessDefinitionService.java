package de.nemeses.spring.bpmn.repository.jpa;

import java.util.Collection;
import java.util.List;

import org.springframework.transaction.support.TransactionTemplate;

import de.nemeses.spring.bpmn.repository.NoSuchProcessDefinitionException;
import de.nemeses.spring.bpmn.repository.ProcessDefinition;
import de.nemeses.spring.bpmn.repository.ProcessDefinitionService;
import jakarta.persistence.EntityManager;

/**
 * JPA backed {@link ProcessDefinitionService}. Every registration creates a new version; nothing is overwritten.
 */
public class JpaProcessDefinitionService implements ProcessDefinitionService {

    private static final String QUERY = "select d from ProcessDefinitionEntity d where d.processDefinitionName = :name "
            + "order by d.created desc, d.id desc";

    private final EntityManager entityManager;
    private final TransactionTemplate transactionTemplate;

    JpaProcessDefinitionService(final EntityManager entityManager, final TransactionTemplate transactionTemplate) {
        this.entityManager = entityManager;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * Stores the definition as a new version. A missing creation date is set to the time of first persistence.
     */
    @Override
    public void register(final ProcessDefinition definition) {
        final ProcessDefinitionEntity entity = new ProcessDefinitionEntity(definition.name(),
                definition.createdDate(), definition.bpmnXml());
        this.transactionTemplate.executeWithoutResult(status -> this.entityManager.persist(entity));
    }

    /**
     * @return the version with the latest creation date
     * @throws NoSuchProcessDefinitionException if no definition of that name exists
     */
    @Override
    public ProcessDefinition retrieveLatest(final String name) throws NoSuchProcessDefinitionException {
        final List<ProcessDefinitionEntity> result = this.transactionTemplate.execute(status -> this.entityManager
                .createQuery(QUERY, ProcessDefinitionEntity.class).setParameter("name", name).setMaxResults(1)
                .getResultList());
        if (result == null || result.isEmpty()) {
            throw new NoSuchProcessDefinitionException(name);
        }
        return result.get(0);
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
