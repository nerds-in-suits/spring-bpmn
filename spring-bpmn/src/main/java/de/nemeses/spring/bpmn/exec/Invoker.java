package de.nemeses.spring.bpmn.exec;

import java.util.Collection;

import de.nemeses.spring.bpmn.model.bpmn20.FlowNode;
import de.nemeses.spring.bpmn.model.bpmn20.SequenceFlow;
import de.nemeses.spring.bpmn.repository.ProcessStep;

/**
 * Triggers and executes the flow nodes of one type. All {@code Invoker} beans are collected by the
 * {@link InvokerRegistry}, which picks the one whose {@link #nodeType()} is the exact class of the node; there must be
 * at most one per node type. A node type without {@code Invoker} is not supported.
 * <p>
 * The work for a node is split in two:
 * <ul>
 * <li>{@link #trigger} is called when the node is reached: by the {@link ProcessEngineService} for the start events
 * of a new instance, by the {@link BpmnNodeExecutor} for the target of each sequence flow taken, and by invokers that
 * enter a scope (the start events of a sub-process, the instances of a multi-instance activity). It creates the
 * step(s) of the node, the trail of the instance, and schedules a {@code bpmnNodeTask} for a step if it is to be
 * executed. It runs in the transaction of the caller, under its lock of the instance, so a
 * {@link ProcessEngineException} fails the triggering step (or the start of the instance), not the node's own
 * step.</li>
 * <li>{@link #execute} is called by the {@link BpmnNodeExecutor} to execute a pending step, in the transaction of the
 * step's task, under the lock of the process instance. It completes the step ({@link NodeExecution#complete()})
 * unless the step has to stay pending (a wait state), and returns the outgoing sequence flows to take; the executor
 * triggers their target nodes.</li>
 * </ul>
 *
 * @param <T> the type of the flow nodes executed
 */
public interface Invoker<T extends FlowNode> {

    /**
     * @return the exact class of the flow nodes this invoker executes (subclasses are not matched)
     */
    Class<T> nodeType();

    /**
     * Reaches the node: creates its pending step and schedules a {@code bpmnNodeTask} for it. Invokers override this
     * if the node needs more steps, other work when it is reached or no task (a wait state that something else
     * resumes).
     *
     * @param node the flow node reached
     * @param trigger how the node is reached: the instance, the incoming flow, the scope and loop data
     * @return the step created for the node
     * @throws ProcessEngineException if the node is not supported or cannot be reached; rolls back the caller
     */
    default ProcessStep trigger(final T node, final NodeTrigger trigger) throws ProcessEngineException {
        return trigger.schedule(trigger.createStep());
    }

    /**
     * Executes one pending step of the node.
     *
     * @param node the flow node of the step
     * @param execution the step being executed, its process instance and process
     * @return the outgoing sequence flows to take; none if the step stays pending or the path ends here
     * @throws ProcessEngineException if the node is not supported or its execution failed; rolls back the step
     */
    Collection<SequenceFlow> execute(T node, NodeExecution execution) throws ProcessEngineException;
}
