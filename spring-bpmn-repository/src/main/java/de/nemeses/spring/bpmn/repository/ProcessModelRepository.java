package de.nemeses.spring.bpmn.repository;

import java.util.Optional;

/**
 *
 */
public interface ProcessModelRepository {

    /**
     * Stores the BPMN 2.0 XML of a process model.
     *
     * @param processId id of the process
     * @param bpmnXml   BPMN 2.0 XML
     */
    void store(String processId, String bpmnXml);

    /**
     * Looks up the BPMN 2.0 XML of a process model.
     *
     * @param processId id of the process
     * @return the XML, if known
     */
    Optional<String> find(String processId);
}
