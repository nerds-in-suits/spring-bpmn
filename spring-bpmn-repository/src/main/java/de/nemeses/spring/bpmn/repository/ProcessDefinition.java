package de.nemeses.spring.bpmn.repository;

import java.util.Date;

public interface ProcessDefinition {
    String name();
    Date createdDate();
    String bpmnXml();
}
