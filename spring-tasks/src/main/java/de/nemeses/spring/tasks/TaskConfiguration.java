package de.nemeses.spring.tasks;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * Registers the {@link TaskService} and enables the {@link TaskProperties}.
 * <p>
 * Scans the components of this package only. Sub-packages belong to other modules (e.g. {@code jpa} of
 * {@code spring-tasks-jpa}, {@code jms} of {@code spring-tasks-jms}) and are not scanned, because their
 * auto-configurations register the {@link TaskPersistence} in their order and under their own conditions.
 */
@SpringBootConfiguration
@ComponentScan(excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX,
        pattern = "de\\.nemeses\\.spring\\.tasks\\.[a-z0-9_]+\\..*"))
@EnableConfigurationProperties(TaskProperties.class)
public class TaskConfiguration {

}
