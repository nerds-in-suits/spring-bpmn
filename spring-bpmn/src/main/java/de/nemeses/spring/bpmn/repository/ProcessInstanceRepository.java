package de.nemeses.spring.bpmn.repository;

import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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
    default ProcessInstance create(final ProcessDefinition definition, final Map<String, Object> variables) {
        return this.create(definition, variables, null);
    }

    /**
     * Persists a new instance, started by a {@code callActivity} step of a parent instance. The repository assigns its
     * id and start date.
     *
     * @param definition the definition version the instance is started from
     * @param variables the initial process variables
     * @param parentStep the call activity step of the parent instance that waits for the new instance, or {@code null}
     *            for a top-level instance
     * @return the persisted instance
     */
    ProcessInstance create(ProcessDefinition definition, Map<String, Object> variables, ProcessStep parentStep);

    /**
     * @param parentStepId the id of a {@code callActivity} step
     * @return the instance started by that step (see {@link ProcessInstance#parentStep()}), if any
     */
    Optional<ProcessInstance> retrieveChild(UUID parentStepId);

    /**
     * @param id the id of the instance
     * @return the stored instance
     * @throws NoSuchProcessInstanceException if no instance with that id exists
     */
    ProcessInstance retrieve(UUID id) throws NoSuchProcessInstanceException;

    /**
     * Retrieves an instance and locks it exclusively until the current transaction ends, so concurrent node
     * executions of one instance are serialized (pessimistic write lock). Must be called in a transaction.
     *
     * @param id the id of the instance
     * @return the stored instance
     * @throws NoSuchProcessInstanceException if no instance with that id exists
     */
    ProcessInstance lock(UUID id) throws NoSuchProcessInstanceException;

    /**
     * Sets the end date of an instance, which marks it as ended.
     *
     * @param id the id of the instance
     * @param endDate the time the instance ended
     * @return the updated instance
     * @throws NoSuchProcessInstanceException if no instance with that id exists
     */
    ProcessInstance updateEndDate(UUID id, Date endDate) throws NoSuchProcessInstanceException;

    /**
     * Replaces the process variables of an instance.
     *
     * @param id the id of the instance
     * @param variables the new process variables, which replace all existing ones
     * @return the updated instance
     * @throws NoSuchProcessInstanceException if no instance with that id exists
     */
    ProcessInstance updateVariables(UUID id, Map<String, Object> variables) throws NoSuchProcessInstanceException;
}
