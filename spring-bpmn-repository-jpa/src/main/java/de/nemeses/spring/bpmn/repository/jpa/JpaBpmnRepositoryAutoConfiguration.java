package de.nemeses.spring.bpmn.repository.jpa;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;

import jakarta.persistence.EntityManager;

/**
 * Skeleton: will register JPA based repository implementations.
 */
@AutoConfiguration(after = HibernateJpaAutoConfiguration.class)
@ConditionalOnBean(EntityManager.class)
public class JpaBpmnRepositoryAutoConfiguration {

}
