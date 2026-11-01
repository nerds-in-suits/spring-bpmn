package de.nemeses.spring.bpmn.repository;

import java.util.Date;
import java.util.UUID;

/**
 * One execution of a flow node of a {@link ProcessInstance}. A step is pending while its {@link #endDate()} is
 * {@code null}. Steps are never deleted, so the steps of an instance are also the trail of its activity. A step
 * inside an embedded sub-process refers to the (pending) step of the sub-process ({@link #parentStepId()}).
 * <p>
 * A multi-instance activity ({@code multiInstanceLoopCharacteristics}) has one step for the activity, which stays
 * pending while its instances run, and one step per instance at the same node, whose {@link #parentStepId()} is the
 * step of the activity; {@link #loopCounter()} and {@link #loopItem()} hold the data of the loop.
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
     * @return for the step of one instance of a multi-instance activity: the 0-based index of the instance; for the
     *         step of a multi-instance activity whose instances were created: the number of instances; else
     *         {@code null}
     */
    Integer loopCounter();

    /**
     * @return for the step of one instance of a multi-instance activity: the element of the input collection the
     *         instance runs for; for the step of a multi-instance activity whose instances were created: a copy of
     *         the input collection; else (and with {@code loopCardinality}) {@code null}
     */
    Object loopItem();

    /**
     * @return the time the step was created
     */
    Date startDate();

    /**
     * @return the time the step was completed, or {@code null} while it is pending
     */
    Date endDate();
}
