package de.nemeses.spring.bpmn;

import java.util.Date;

import de.nemeses.spring.bpmn.repository.ProcessDefinition;

record ParsedProcessDefinition(String name, Date createdDate, String bpmnXml) implements ProcessDefinition {
}
