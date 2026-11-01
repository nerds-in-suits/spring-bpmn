package de.nemeses.spring.bpmn.exec;

import de.nemeses.spring.bpmn.model.bpmn20.FlowNode;

/**
 * Adds behaviour to {@link Invoker}s where needed, e.g. a loop around the execution of an activity, by wrapping them
 * in another invoker of the same node type. All {@code InvokerEnhancer} beans are injected into the
 * {@link BpmnNodeExecutor}, which passes every invoker through each of them in turn, so an enhancer may get an invoker
 * that another enhancer already wrapped.
 */
public interface InvokerEnhancer {

    /**
     * @param <T> the type of flow node
     * @param invoker an invoker, possibly already enhanced
     * @return an invoker wrapping the given one, or the given one itself if this enhancer does not apply to its node
     *         type
     */
    <T extends FlowNode> Invoker<T> enhance(Invoker<T> invoker);
}
