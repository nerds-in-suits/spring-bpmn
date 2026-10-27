package de.nemeses.spring.bpmn.repository;

import java.util.Map;

/**
 * Stores {@link ProcessInstance}s.
 */
public interface ProcessInstanceRepository {

    /**
     * Persists a new instance. The repository assigns its id and start date.
     *
     * @param definition the definition version the instance is started from
     * @param variables the initial process variables
     * @return the persisted instance
     */
    ProcessInstance create(ProcessDefinition definition, Map<String, Object> variables);
}
