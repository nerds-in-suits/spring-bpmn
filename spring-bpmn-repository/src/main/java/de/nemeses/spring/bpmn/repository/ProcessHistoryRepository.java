package de.nemeses.spring.bpmn.repository;

/**
 *
 */
public interface ProcessHistoryRepository {

    /**
     * Appends an audit log entry for a process instance.
     *
     * @param processInstanceId id of the process instance
     * @param message           audit message
     */
    void append(String processInstanceId, String message);
}
