package de.nemeses.spring.tasks.jpa;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import de.nemeses.spring.tasks.TaskPersistence;
import jakarta.persistence.EntityManagerFactory;

/**
 * Registers {@link JpaTaskPersistence} if there is an {@link EntityManagerFactory} and no other
 * {@link TaskPersistence}; adds the package of {@link TaskEntity} to the scanned entity packages.
 */
@AutoConfiguration(after = HibernateJpaAutoConfiguration.class)
@AutoConfigurationPackage(basePackageClasses = TaskEntity.class)
@ConditionalOnBean(EntityManagerFactory.class)
@ConditionalOnMissingBean(TaskPersistence.class)
@EnableConfigurationProperties(JpaTaskProperties.class)
public class JpaTaskPersistenceAutoConfiguration {

    @Bean
    JpaTaskPersistence taskPersistence(final EntityManagerFactory entityManagerFactory,
            final PlatformTransactionManager transactionManager, final JpaTaskProperties properties) {
        return new JpaTaskPersistence(SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory),
                new TransactionTemplate(transactionManager), properties.maxResults());
    }
}
