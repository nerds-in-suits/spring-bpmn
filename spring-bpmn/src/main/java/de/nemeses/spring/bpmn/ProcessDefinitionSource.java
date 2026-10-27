package de.nemeses.spring.bpmn;

import java.util.Collection;

import org.springframework.core.io.Resource;

/**
 * Contributes BPMN documents that are registered as process definitions on startup.
 */
public interface ProcessDefinitionSource {

    Collection<Resource> resources();
}
