package de.nemeses.spring.bpmn.rest;

import java.time.Instant;

import de.nemeses.spring.bpmn.repository.ProcessDefinition;
import de.nemeses.spring.bpmn.repository.ProcessInstanceCount;

/**
 * The latest version of a process definition with the number of its instances, as listed by
 * {@code GET /process-definitions}.
 *
 * @param name the process definition name
 * @param createdDate the time the latest version was registered
 * @param md5Hash the MD5 hash of the BPMN XML of the latest version
 * @param runningInstances the number of running instances of all versions
 * @param finishedInstances the number of finished instances of all versions
 */
public record ProcessDefinitionSummaryDto(
        String name,
        Instant createdDate,
        String md5Hash,
        long runningInstances,
        long finishedInstances) {

    /**
     * @param definition the latest version of a process definition
     * @param count the instance counts of the definition, {@code null} if it has no instances
     * @return its response
     */
    static ProcessDefinitionSummaryDto from(final ProcessDefinition definition, final ProcessInstanceCount count) {
        final ProcessInstanceCount counted = count == null ? new ProcessInstanceCount(0, 0) : count;
        return new ProcessDefinitionSummaryDto(definition.name(), Dates.instant(definition.createdDate()),
                definition.md5Hash(), counted.running(), counted.finished());
    }
}
