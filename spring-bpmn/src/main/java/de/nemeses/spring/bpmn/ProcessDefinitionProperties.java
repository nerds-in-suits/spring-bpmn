package de.nemeses.spring.bpmn;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration properties of the process definition registration.
 *
 * @param processDefinitionLocations resource patterns scanned for BPMN documents on startup.
 */
@ConfigurationProperties(prefix = "spring.bpmn")
public record ProcessDefinitionProperties(
        @DefaultValue("classpath*:/process-definitions/*.bpmn") List<String> processDefinitionLocations) {
}
