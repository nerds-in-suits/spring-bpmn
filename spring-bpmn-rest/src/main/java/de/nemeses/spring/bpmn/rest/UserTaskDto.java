package de.nemeses.spring.bpmn.rest;

import java.time.Instant;
import java.util.UUID;

import de.nemeses.spring.bpmn.repository.ProcessStep;

/**
 * An open user task: a pending step at a {@code userTask} node, identified by the id of the step.
 *
 * @param id the id of the step, used to complete the task
 * @param processInstanceId the id of the process instance
 * @param nodeId the id of the {@code userTask}
 * @param nodeName the name of the {@code userTask}, {@code null} if it has none
 * @param parentStepId the step of the enclosing sub-process or multi-instance activity, {@code null} at top level
 * @param loopCounter the loop counter of a multi-instance user task, else {@code null}
 * @param loopItem the loop item of a multi-instance user task, else {@code null}
 * @param startDate the time the task was created
 */
public record UserTaskDto(UUID id, UUID processInstanceId, String nodeId, String nodeName, UUID parentStepId,
        Integer loopCounter, Object loopItem, Instant startDate) {

    /**
     * @param step a pending user task step
     * @param node its flow node, {@code null} if the node is not in the model
     * @return its response
     */
    static UserTaskDto from(final ProcessStep step, final ProcessNodes.Node node) {
        return new UserTaskDto(step.id(), step.processInstanceId(), step.nodeId(), node == null ? null : node.name(),
                step.parentStepId(), step.loopCounter(), step.loopItem(), Dates.instant(step.startDate()));
    }
}
