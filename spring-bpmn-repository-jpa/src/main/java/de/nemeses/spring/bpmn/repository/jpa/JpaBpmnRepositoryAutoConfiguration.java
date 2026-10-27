package de.nemeses.spring.bpmn.repository.jpa;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import de.nemeses.spring.bpmn.repository.ProcessDefinitionService;
import jakarta.persistence.EntityManagerFactory;

/**
 * Registers the JPA based repository implementations.
 */
@AutoConfiguration(after = HibernateJpaAutoConfiguration.class)
@AutoConfigurationPackage(basePackageClasses = ProcessDefinitionEntity.class)
@ConditionalOnBean(EntityManagerFactory.class)
@Import(JpaProcessInstanceRepository.class)
public class JpaBpmnRepositoryAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ProcessDefinitionService.class)
    JpaProcessDefinitionService processDefinitionService(final EntityManagerFactory entityManagerFactory,
            final PlatformTransactionManager transactionManager) {
        return new JpaProcessDefinitionService(
                SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory),
                new TransactionTemplate(transactionManager));
    }
}
