package de.nemeses.spring.bpmn.repository;

import java.util.Collection;
import java.util.List;

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

    /**
     * Lists the newest version of every process definition name. Ordered by name, so that a list of the definitions
     * shown to users is stable.
     *
     * @return the newest version of each definition, ordered by name; empty if none is registered
     */
    List<ProcessDefinition> retrieveLatestAll();
}
