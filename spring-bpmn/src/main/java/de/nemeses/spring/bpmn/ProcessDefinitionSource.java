package de.nemeses.spring.bpmn;

import java.io.IOException;
import java.util.Collection;

import org.springframework.core.io.Resource;

/**
 * Contributes BPMN documents that are registered as process definitions on startup.
 */
public interface ProcessDefinitionSource {

    /**
     * @return the BPMN documents to register
     * @throws IOException if the resources cannot be resolved
     */
    Collection<Resource> resources() throws IOException;
}
