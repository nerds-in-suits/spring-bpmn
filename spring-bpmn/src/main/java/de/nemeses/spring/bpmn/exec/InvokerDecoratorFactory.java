package de.nemeses.spring.bpmn.exec;

import de.nemeses.spring.bpmn.model.bpmn20.FlowNode;

/**
 * Decorates {@link Invoker}s with {@link InvokerDecorator}s. All {@code InvokerDecoratorFactory} beans are injected
 * into the {@link BpmnNodeExecutor}, which passes every invoker through each of them in turn, so a factory may get an
 * invoker that another factory already decorated.
 */
public interface InvokerDecoratorFactory {

    /**
     * @param <T> the type of flow node
     * @param invoker an invoker, possibly already decorated
     * @return a decorator of the invoker, or the invoker itself if this factory does not decorate its node type
     */
    <T extends FlowNode> Invoker<T> decorate(Invoker<T> invoker);
}
