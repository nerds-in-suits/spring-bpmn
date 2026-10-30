package de.nemeses.spring.bpmn.repository;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Stores the {@link ProcessStep}s of process instances. Steps are never deleted.
 */
public interface ProcessStepRepository {

    /**
     * Persists a new pending step. The repository assigns its id and start date (now).
     *
     * @param instanceId the id of the owning process instance
     * @param nodeId the id of the flow node to execute
     * @param incomingFlowId the id of the sequence flow the step arrived on, {@code null} for a start event
     * @param parentStepId the id of the step of the embedded sub-process the new step runs in, {@code null} at the
     *            top level of the process
     * @return the persisted step
     * @throws NoSuchProcessInstanceException if no instance with that id exists
     */
    ProcessStep create(UUID instanceId, String nodeId, String incomingFlowId, UUID parentStepId)
            throws NoSuchProcessInstanceException;

    /**
     * @param id the id of the step
     * @return the stored step
     * @throws NoSuchProcessStepException if no step with that id exists
     */
    ProcessStep retrieve(UUID id) throws NoSuchProcessStepException;

    /**
     * Reads the pending steps from the store, so steps completed by a transaction that committed after they were
     * read before are not returned.
     *
     * @param instanceId the id of the process instance
     * @param nodeId the id of the flow node
     * @return the pending steps of the instance at that node, oldest first
     */
    List<ProcessStep> pending(UUID instanceId, String nodeId);

    /**
     * Reads the steps that ran or run inside an embedded sub-process from the store, like {@link #pending}.
     *
     * @param parentStepId the id of the step of the sub-process
     * @return the steps whose {@link ProcessStep#parentStepId()} is that id (pending and completed), oldest first
     */
    List<ProcessStep> children(UUID parentStepId);

    /**
     * @param instanceId the id of the process instance
     * @return the number of pending steps of the instance
     */
    long countPending(UUID instanceId);

    /**
     * Completes steps by setting their end date.
     *
     * @param ids the ids of the steps
     * @param endDate the time the steps were completed
     * @throws NoSuchProcessStepException if one of the steps does not exist
     */
    void complete(Collection<UUID> ids, Date endDate) throws NoSuchProcessStepException;

    /**
     * @param instanceId the id of the process instance
     * @return all steps of the instance, ordered by start date
     */
    List<ProcessStep> steps(UUID instanceId);
}
