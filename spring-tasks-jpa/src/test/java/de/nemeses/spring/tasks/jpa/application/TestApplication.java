package de.nemeses.spring.tasks.jpa.application;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Configuration;

/**
 * Test application in its own package, so the entity of the JPA task persistence is not found by the default scan.
 */
@Configuration
@EnableAutoConfiguration
public class TestApplication {
}
