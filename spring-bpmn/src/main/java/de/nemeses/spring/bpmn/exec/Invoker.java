package de.nemeses.spring.bpmn.exec;

import java.util.List;

import de.nemeses.spring.bpmn.model.bpmn20.FlowNode;
import de.nemeses.spring.bpmn.model.bpmn20.SequenceFlow;

/**
 * Executes the flow nodes of one type for the {@link BpmnNodeExecutor}. All {@code Invoker} beans are injected into
 * the executor, which picks the one whose {@link #nodeType()} is the exact class of the node to execute; there must be
 * at most one per node type. A node type without {@code Invoker} is not supported.
 * <p>
 * The invoker runs in the transaction of the step, under the lock of the process instance. It completes the step
 * ({@link NodeExecution#complete()}) unless the step has to stay pending (a wait state), and returns the outgoing
 * sequence flows to take; the executor creates the follow-up steps for them.
 *
 * @param <T> the type of the flow nodes executed
 */
public interface Invoker<T extends FlowNode> {

    /**
     * @return the exact class of the flow nodes this invoker executes (subclasses are not matched)
     */
    Class<T> nodeType();

    /**
     * Executes one step of the node.
     *
     * @param node the flow node of the step
     * @param execution the step being executed, its process instance and process
     * @return the outgoing sequence flows to take; none if the step stays pending or the path ends here
     * @throws ProcessEngineException if the node is not supported or its execution failed; rolls back the step
     */
    List<SequenceFlow> invoke(T node, NodeExecution execution) throws ProcessEngineException;
}
