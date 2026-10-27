package de.nemeses.spring.bpmn;

import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import de.nemeses.spring.tasks.JsonPayloadTaskImplementation;

/**
 * Executes a single node of a process instance. Node execution is not implemented yet; the task only logs.
 */
@Component(BpmnNodeTask.BEAN_NAME)
public class BpmnNodeTask extends JsonPayloadTaskImplementation<NodeTaskPayload> {

    /** Bean name under which the task is scheduled with the {@code TaskService}. */
    public static final String BEAN_NAME = "bpmnNodeTask";

    BpmnNodeTask() {
        super();
    }

    @Override
    protected Class<NodeTaskPayload> payloadType() {
        return NodeTaskPayload.class;
    }

    @Override
    public void execute(final NodeTaskPayload payload) {
        LoggerFactory.getLogger(this.getClass()).info("Node {} of process instance {} is due; node execution is not implemented yet.",
                payload.nodeId(), payload.processId());
    }
}
