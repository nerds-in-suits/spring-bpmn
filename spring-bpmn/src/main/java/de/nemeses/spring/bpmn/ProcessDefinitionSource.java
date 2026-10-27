package de.nemeses.spring.bpmn;

import java.util.stream.Stream;

import org.springframework.core.io.Resource;

/**
 * Contributes BPMN documents that are registered as process definitions on startup.
 */
public interface ProcessDefinitionSource {

    /**
     * @return the BPMN documents to register
     */
    Stream<Resource> resources();
}
