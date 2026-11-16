package de.nemeses.spring.bpmn.rest;

import java.time.Instant;
import java.util.UUID;

import de.nemeses.spring.bpmn.repository.ProcessInstance;
import de.nemeses.spring.bpmn.repository.ProcessStep;

/**
 * A process instance without its variables, as listed by {@code GET /process-instances}.
 *
 * @param id the instance id
 * @param processDefinition the name of its process definition
 * @param processDefinitionCreatedDate the creation date of the definition version it was started from
 * @param state {@code running} or {@code finished}
 * @param startDate the time it was started
 * @param endDate the time it ended, {@code null} while running
 * @param parentInstanceId the instance whose call activity started it, {@code null} for a top-level instance
 * @param parentStepId the call activity step that started it, {@code null} for a top-level instance
 */
public record ProcessInstanceSummaryDto(
        UUID id,
        String processDefinition,
        Instant processDefinitionCreatedDate,
        String state,
        Instant startDate,
        Instant endDate,
        UUID parentInstanceId,
        UUID parentStepId) {

    /**
     * @param instance a process instance
     * @return its response
     */
    static ProcessInstanceSummaryDto from(final ProcessInstance instance) {
        final ProcessStep parent = instance.parentStep();
        return new ProcessInstanceSummaryDto(instance.id(), instance.processDefinition().name(),
                Dates.instant(instance.processDefinition().createdDate()), State.from(instance.endDate()).value(),
                Dates.instant(instance.startDate()), Dates.instant(instance.endDate()),
                parent == null ? null : parent.processInstanceId(), parent == null ? null : parent.id());
    }
}
