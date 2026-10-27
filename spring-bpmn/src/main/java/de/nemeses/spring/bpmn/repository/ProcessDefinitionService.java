package de.nemeses.spring.bpmn.repository;

import java.util.Collection;

/**
 * Service to manage {@link ProcessDefinition}s.
 */
public interface ProcessDefinitionService {

    void register(ProcessDefinition definition);

    /**
     * @param name the process definition name
     * @return the newest version of the definition
     * @throws NoSuchProcessDefinitionException if no definition with that name exists
     */
    ProcessDefinition retrieveLatest(String name) throws NoSuchProcessDefinitionException;

    Collection<ProcessDefinition> retrieveAll(String name);
}
