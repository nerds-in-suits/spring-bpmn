package de.nemeses.spring.bpmn;

/**
 * Payload of the {@link BpmnNodeTask}: the node of a process instance to execute.
 *
 * @param processId the id of the process instance
 * @param nodeId the id of the BPMN flow node
 */
public record NodeTaskPayload(String processId, String nodeId) {
}
