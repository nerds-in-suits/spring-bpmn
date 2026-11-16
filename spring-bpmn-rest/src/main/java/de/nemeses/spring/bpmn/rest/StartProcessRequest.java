package de.nemeses.spring.bpmn.rest;

import java.util.Map;

/**
 * Body of {@code POST /process-instances}.
 *
 * @param processDefinition the name of the process definition whose latest version is started; required
 * @param variables the initial process variables, may be missing
 */
public record StartProcessRequest(String processDefinition, Map<String, Object> variables) {
}
