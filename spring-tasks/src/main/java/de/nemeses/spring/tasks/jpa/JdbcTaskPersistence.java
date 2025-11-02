package de.nemeses.spring.tasks.jpa;

import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import de.nemeses.spring.tasks.TaskPersistence;
import jakarta.annotation.PostConstruct;

/**
 *
 */
public class JdbcTaskPersistence implements TaskPersistence {
    private final JdbcTemplate template;

    JdbcTaskPersistence(final JdbcTemplate template) {
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
