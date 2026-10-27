package de.nemeses.spring.tasks;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

/**
 *
 */
@SpringBootConfiguration
@ComponentScan
@EnableConfigurationProperties(TaskProperties.class)
public class TaskConfiguration {

}
