package de.nemeses.spring.bpmn.rest;

import java.time.Instant;

import de.nemeses.spring.bpmn.repository.ProcessDefinition;

/**
 * One version of a process definition, without its BPMN XML.
 *
 * @param name the process definition name
 * @param createdDate the time the version was registered
 * @param md5Hash the MD5 hash of the BPMN XML of the version
 */
public record ProcessDefinitionDto(
        String name,
        Instant createdDate,
        String md5Hash) {

    /**
     * @param definition a process definition version
     * @return its response
     */
    static ProcessDefinitionDto from(final ProcessDefinition definition) {
        return new ProcessDefinitionDto(definition.name(), Dates.instant(definition.createdDate()),
                definition.md5Hash());
    }
}
