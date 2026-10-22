package de.nemeses.spring.tasks.jpa;

import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import de.nemeses.spring.tasks.TaskPersistence;
import jakarta.persistence.EntityManager;

/**
 *
 */
public class JpaTaskPersistence implements TaskPersistence {
    private final EntityManager entityManager;

    JpaTaskPersistence(final EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public void persist(final Data data) {
        LoggerFactory.getLogger(this.getClass()).info("Persisting {}", data);
        this.entityManager.persist(new TaskEntity(data.beanName(), data.payload(), data.earliestExecution()));
    }
}
