package de.nemeses.spring.bpmn.repository;

import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * A running or finished instance of a {@link ProcessDefinition}.
 */
public interface ProcessInstance {

    /**
     * @return the unique id assigned by the {@link ProcessInstanceRepository}
     */
    @SuppressWarnings("PMD.ShortMethodName")
    UUID id();

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

    /**
     * @return the pending {@code callActivity} step of the parent instance that started this instance and waits for it
     *         to end, or {@code null} for an instance that was not started by a call activity
     */
    ProcessStep parentStep();

    /**
     * @return the instance owning the {@link #parentStep()}, or {@code null} for an instance that was not started by a
     *         call activity
     */
    ProcessInstance parentInstance();
}
