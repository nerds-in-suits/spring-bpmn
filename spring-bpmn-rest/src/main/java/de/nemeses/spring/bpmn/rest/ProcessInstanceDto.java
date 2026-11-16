package de.nemeses.spring.bpmn.rest;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import de.nemeses.spring.bpmn.repository.ProcessInstance;

/**
 * A process instance with its variables, as returned by {@code GET /process-instances/{id}}.
 *
 * @param id the instance id
 * @param processDefinition the name of its process definition
 * @param processDefinitionCreatedDate the creation date of the definition version it was started from
 * @param state {@code running} or {@code finished}
 * @param startDate the time it was started
 * @param endDate the time it ended, {@code null} while running
 * @param parentInstanceId the instance whose call activity started it, {@code null} for a top-level instance
 * @param parentStepId the call activity step that started it, {@code null} for a top-level instance
 * @param variables the process variables
 */
public record ProcessInstanceDto(
        UUID id,
        String processDefinition,
        Instant processDefinitionCreatedDate,
        String state,
        Instant startDate,
        Instant endDate,
        UUID parentInstanceId,
        UUID parentStepId,
        Map<String, Object> variables) {

    /**
     * @param instance a process instance
     * @return its response
     */
    static ProcessInstanceDto from(final ProcessInstance instance) {
        final ProcessInstanceSummaryDto summary = ProcessInstanceSummaryDto.from(instance);
        return new ProcessInstanceDto(
                summary.id(),
                summary.processDefinition(),
                summary.processDefinitionCreatedDate(),
                summary.state(),
                summary.startDate(),
                summary.endDate(),
                summary.parentInstanceId(),
                summary.parentStepId(),
                instance.variables());
    }
}
