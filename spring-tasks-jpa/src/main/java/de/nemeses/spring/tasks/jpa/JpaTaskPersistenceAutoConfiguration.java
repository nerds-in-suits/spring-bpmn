package de.nemeses.spring.tasks.jpa;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import de.nemeses.spring.tasks.TaskPersistence;
import jakarta.persistence.EntityManager;

/**
 *
 */
@AutoConfiguration(after = HibernateJpaAutoConfiguration.class)
@ConditionalOnBean({ EntityManager.class })
@ConditionalOnMissingBean(TaskPersistence.class)
@EnableConfigurationProperties(JpaTaskProperties.class)
public class JpaTaskPersistenceAutoConfiguration {

    @Bean
    JpaTaskPersistence taskPersistence(final EntityManager entityManager,
            final PlatformTransactionManager transactionManager, final JpaTaskProperties properties) {
        return new JpaTaskPersistence(entityManager, new TransactionTemplate(transactionManager),
                properties.maxResults());
    }
}
