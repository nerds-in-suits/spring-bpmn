package de.nemeses.spring.bpmn.rest;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import de.nemeses.spring.tasks.TaskConfiguration;

/**
 * Test application of the controller and security tests: the REST auto-configuration on the real engine, with the
 * JPA repositories and the JPA task persistence on H2, plus {@link TestProcesses} to set up data.
 * <p>
 * The tests of one application context share its database, so each test works on process definitions of its own,
 * named by {@link TestProcesses#uniqueName}.
 */
@Configuration
@EnableAutoConfiguration
@Import({ TaskConfiguration.class, TestProcesses.class })
class TestEngineConfiguration {
}
