package de.nemeses.spring.bpmn.exec;

import de.nemeses.spring.bpmn.model.bpmn20.FlowNode;

/**
 * An {@link Invoker} that adds behaviour to another invoker of the same node type, e.g. a loop around the execution of
 * an activity. Decorators are created by the {@link InvokerDecoratorFactory} beans when the {@link BpmnNodeExecutor}
 * registers its invokers.
 *
 * @param <T> the type of flow node
 */
public interface InvokerDecorator<T extends FlowNode> extends Invoker<T> {

    /**
     * @return the decorated invoker
     */
    Invoker<T> delegate();

    /**
     * @return the node type of the {@link #delegate()}
     */
    @Override
    default Class<T> nodeType() {
        return this.delegate().nodeType();
    }
}
