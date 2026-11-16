package de.nemeses.spring.bpmn.rest;

import java.time.Instant;

import de.nemeses.spring.bpmn.repository.ProcessDefinition;

/**
 * One process of a document deployed with {@code POST /process-definitions}.
 *
 * @param name the process definition name
 * @param createdDate the time the latest version was registered
 * @param md5Hash the MD5 hash of the BPMN XML of the latest version
 * @param registered {@code true} if the deployment registered a new version, {@code false} if the latest version was
 *            unchanged
 */
public record DeployedProcessDefinitionDto(
        String name,
        Instant createdDate,
        String md5Hash,
        boolean registered) {

    /**
     * @param definition the latest version of the process definition after the deployment
     * @param registered whether the deployment registered it
     * @return its response
     */
    static DeployedProcessDefinitionDto from(final ProcessDefinition definition, final boolean registered) {
        return new DeployedProcessDefinitionDto(definition.name(), Dates.instant(definition.createdDate()),
                definition.md5Hash(), registered);
    }
}
