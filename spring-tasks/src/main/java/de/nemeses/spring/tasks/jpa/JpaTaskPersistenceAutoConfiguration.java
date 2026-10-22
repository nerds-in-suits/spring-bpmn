package de.nemeses.spring.tasks.jpa;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import de.nemeses.spring.tasks.TaskPersistence;
import jakarta.persistence.EntityManager;

/**
 *
 */
@AutoConfiguration
@ConditionalOnBean({ EntityManager.class })
@ConditionalOnMissingBean(TaskPersistence.class)
public class JpaTaskPersistenceAutoConfiguration {

    @Bean
    JpaTaskPersistence taskPersistence(final EntityManager entityManager) {
        return new JpaTaskPersistence(entityManager);
    }
}
