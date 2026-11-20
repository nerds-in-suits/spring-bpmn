package de.nemeses.spring.bpmn.ui;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import de.nemeses.spring.tasks.TaskConfiguration;

/**
 * Test application: the REST and UI auto-configurations on the real engine, with the JPA repositories and the JPA
 * task persistence on H2.
 */
@Configuration
@EnableAutoConfiguration
@Import(TaskConfiguration.class)
class UiTestConfiguration {
}
