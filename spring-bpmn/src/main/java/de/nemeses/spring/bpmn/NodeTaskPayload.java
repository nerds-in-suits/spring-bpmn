package de.nemeses.spring.bpmn;

import java.util.UUID;

/**
 * Payload of the {@link BpmnNodeTask}: the node of a process instance to execute.
 *
 * @param processId the id of the process instance
 * @param nodeId the id of the BPMN flow node
 */
public record NodeTaskPayload(UUID processId, String nodeId) {
}
