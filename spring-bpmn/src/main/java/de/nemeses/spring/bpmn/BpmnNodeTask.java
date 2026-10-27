package de.nemeses.spring.bpmn;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import de.nemeses.spring.tasks.JsonPayloadTaskImplementation;
import de.nemeses.spring.tasks.TaskException;

/**
 * Executes a single node of a process instance through {@link BpmnNodeExecutor#executeNode}.
 */
@Component(BpmnNodeTask.BEAN_NAME)
public class BpmnNodeTask extends JsonPayloadTaskImplementation<NodeTaskPayload> {

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
    protected Class<NodeTaskPayload> payloadType() {
        return NodeTaskPayload.class;
    }

    @Override
    public void execute(final NodeTaskPayload payload) throws TaskException {
        try {
            this.executor.getObject().executeNode(payload.processId(), payload.nodeId());
        } catch (final ProcessEngineException e) {
            throw new TaskException("Cannot execute node " + payload.nodeId() + " of process instance "
                    + payload.processId() + ".", e);
        }
    }
}
