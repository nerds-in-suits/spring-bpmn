package de.nemeses.spring.tasks.jpa;

import org.slf4j.LoggerFactory;

import de.nemeses.spring.tasks.TaskPersistence;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;

/**
 *
 */
public class JpaTaskPersistence implements TaskPersistence {
    private final EntityManager template;

    JpaTaskPersistence(final EntityManager template) {
        this.template = template;
    }

    @PostConstruct
    public void assureTables() {
        //
    }

    @Override
    public void persist(final Data data) {
        LoggerFactory.getLogger(this.getClass()).info("Persisting {}", data);
        this.template.update(
            "INSERT INTO "+this.tableName()+" (beanname, payload, execution) VALUES (?, ?, ?)",
            data.beanName(), data.payload(), data.earliestExecution());
    }

    private String tableName() {
        return "spring_tasks";
    }
}
