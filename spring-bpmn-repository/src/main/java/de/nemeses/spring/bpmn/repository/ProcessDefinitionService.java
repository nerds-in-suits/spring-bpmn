package de.nemeses.spring.bpmn.repository;

import java.util.Collection;

/**
 * Service to manage {@link ProcessDefinition}s.
 */
public interface ProcessDefinitionService {

    void register(ProcessDefinition definition);

    ProcessDefinition retrieveLatest(String name);

    Collection<ProcessDefinition> retrieveAll(String name);
}
