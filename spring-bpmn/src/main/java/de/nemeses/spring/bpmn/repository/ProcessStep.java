package de.nemeses.spring.bpmn.repository;

import java.util.Date;
import java.util.UUID;

/**
 * One execution of a flow node of a {@link ProcessInstance}. A step is pending while its {@link #endDate()} is
 * {@code null}. Steps are never deleted, so the steps of an instance are also the trail of its activity. A step
 * inside an embedded sub-process refers to the (pending) step of the sub-process ({@link #parentStepId()}).
 */
public interface ProcessStep {

    /**
     * @return the unique id assigned by the {@link ProcessStepRepository}
     */
    @SuppressWarnings("PMD.ShortMethodName")
    UUID id();

    /**
     * @return the id of the owning process instance
     */
    UUID processInstanceId();

    /**
     * @return the id of the flow node this step executes
     */
    String nodeId();

    /**
     * @return the id of the sequence flow the step arrived on, or {@code null} for a start event
     */
    String incomingFlowId();

    /**
     * @return the id of the step of the embedded sub-process this step runs in, or {@code null} for a step at the
     *         top level of the process
     */
    UUID parentStepId();

    /**
     * @return the time the step was created
     */
    Date startDate();

    /**
     * @return the time the step was completed, or {@code null} while it is pending
     */
    Date endDate();
}
