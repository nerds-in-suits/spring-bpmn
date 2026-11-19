package de.nemeses.spring.bpmn.rest;

import java.time.Instant;
import java.util.UUID;

import de.nemeses.spring.bpmn.repository.ProcessStep;

/**
 * One step (execution of a flow node) of a process instance, as listed by
 * {@code GET /process-instances/{id}/steps}.
 *
 * @param id the step id
 * @param nodeId the id of the flow node
 * @param nodeName the name of the flow node, {@code null} if it has none
 * @param type the BPMN element of the flow node, e.g. {@code userTask}; {@code null} if the node is not in the model
 * @param state {@code running} (pending) or {@code finished}
 * @param incomingFlowId the sequence flow the step arrived on, {@code null} for a start event
 * @param parentStepId the step of the enclosing sub-process or multi-instance activity, {@code null} at top level
 * @param loopCounter the loop counter of a multi-instance activity, else {@code null}
 * @param loopItem the loop item of a multi-instance activity, else {@code null}
 * @param startDate the time the step was created
 * @param endDate the time the step was completed, {@code null} while pending
 * @param errorDate the time executing the step failed the last time, {@code null} if it never failed
 * @param errorMessage the message of the last failure, {@code null} if there is none
 * @param errorStackTrace the stack trace of the last failure with all its causes, {@code null} if it never failed
 */
public record ProcessStepDto(
        UUID id,
        String nodeId,
        String nodeName,
        String type,
        String state,
        String incomingFlowId,
        UUID parentStepId,
        Integer loopCounter,
        Object loopItem,
        Instant startDate,
        Instant endDate,
        Instant errorDate,
        String errorMessage,
        String errorStackTrace) {

    /**
     * @param step a step
     * @param node its flow node, {@code null} if the node is not in the model
     * @return its response
     */
    static ProcessStepDto from(final ProcessStep step, final ProcessNodes.Node node) {
        return new ProcessStepDto(
                step.id(),
                step.nodeId(),
                node == null ? null : node.name(),
                node == null ? null : node.type(),
                State.from(step.endDate()).value(),
                step.incomingFlowId(),
                step.parentStepId(),
                step.loopCounter(),
                step.loopItem(),
                Dates.instant(step.startDate()),
                Dates.instant(step.endDate()),
                Dates.instant(step.errorDate()),
                step.errorMessage(),
                step.errorStackTrace());
    }
}
