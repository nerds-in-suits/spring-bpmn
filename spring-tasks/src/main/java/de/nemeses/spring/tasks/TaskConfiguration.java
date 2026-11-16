package de.nemeses.spring.tasks;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

/**
 * Registers the {@link TaskService} and enables the {@link TaskProperties}.
 * <p>
 * The beans are registered explicitly, without a component scan, so the sub-packages of other modules (e.g.
 * {@code jpa} of {@code spring-tasks-jpa}, {@code jms} of {@code spring-tasks-jms}) are never picked up; their
 * auto-configurations register the {@link TaskPersistence} in their order and under their own conditions.
 */
@SpringBootConfiguration
@Import(TaskService.class)
@EnableConfigurationProperties(TaskProperties.class)
public class TaskConfiguration {

}
