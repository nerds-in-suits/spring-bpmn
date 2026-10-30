package de.nemeses.spring.bpmn;

import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import de.nemeses.spring.tasks.JsonPayloadTaskImplementation;
import de.nemeses.spring.tasks.TaskException;

/**
 * Executes one process step (a node of a process instance) through {@link BpmnNodeExecutor#executeNode}. The payload
 * is the id of the {@link de.nemeses.spring.bpmn.repository.ProcessStep}. A {@link ProcessEngineException} becomes a
 * {@link TaskException}, so the task fails; whether it is retried depends on the task backend.
 */
@Component(BpmnNodeTask.BEAN_NAME)
public class BpmnNodeTask extends JsonPayloadTaskImplementation<UUID> {

    /** Bean name under which the task is scheduled with the {@code TaskService}. */
    public static final String BEAN_NAME = "bpmnNodeTask";

    /**
     * Resolved lazily: the executor depends on the {@code TaskService}, which depends on all task implementations.
     */
    private final ObjectProvider<BpmnNodeExecutor> executor;

    BpmnNodeTask(final ObjectProvider<BpmnNodeExecutor> executor) {
        super();
        this.executor = executor;
    }

    @Override
    protected Class<UUID> payloadType() {
        return UUID.class;
    }

    @Override
    public void execute(final UUID stepId) throws TaskException {
        try {
            this.executor.getObject().executeNode(stepId);
        } catch (final ProcessEngineException e) {
            throw new TaskException("Cannot execute process step " + stepId + ".", e);
        }
    }
}
