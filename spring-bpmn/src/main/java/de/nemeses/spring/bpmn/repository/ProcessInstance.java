package de.nemeses.spring.bpmn.repository;

import java.util.Date;
import java.util.Map;

/**
 * A running or finished instance of a {@link ProcessDefinition}.
 */
public interface ProcessInstance {

    /**
     * @return the unique id (UUID string) assigned by the {@link ProcessInstanceRepository}
     */
    @SuppressWarnings("PMD.ShortMethodName")
    String id();

    /**
     * @return the exact version of the definition this instance was started from
     */
    ProcessDefinition processDefinition();

    /**
     * @return the process variables
     */
    Map<String, Object> variables();

    /**
     * @return the time the instance was started
     */
    Date startDate();

    /**
     * @return the time the instance ended, or {@code null} while it is running
     */
    Date endDate();
}
