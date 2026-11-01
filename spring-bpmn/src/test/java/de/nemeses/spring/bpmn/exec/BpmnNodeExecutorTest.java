package de.nemeses.spring.bpmn.exec;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import de.nemeses.spring.bpmn.model.bpmn20.FlowNode;
import de.nemeses.spring.bpmn.model.bpmn20.SequenceFlow;
import de.nemeses.spring.bpmn.model.bpmn20.Task;

/**
 * Checks how {@link BpmnNodeExecutor} collects its {@link Invoker}s.
 */
class BpmnNodeExecutorTest {

    @Test
    void testTwoInvokersForOneNodeTypeFail() {
        final List<Invoker<?>> invokers = List.of(new TaskStub(), new TaskStub());
        final IllegalStateException e = Assertions.assertThrows(IllegalStateException.class,
                () -> new BpmnNodeExecutor(null, null, null, null, invokers, List.of()));
        Assertions.assertTrue(e.getMessage().contains("Task"), e.getMessage());
    }

    @Test
    void testOneInvokerPerNodeType() {
        Assertions.assertDoesNotThrow(() -> new BpmnNodeExecutor(null, null, null, null, List.of(new TaskStub()),
                List.of()));
    }

    @Test
    void testDecoratorFactoriesDecorateEveryInvokerInTurn() {
        final TaskStub invoker = new TaskStub();
        final List<String> calls = new ArrayList<>();
        new BpmnNodeExecutor(null, null, null, null, List.of(invoker), List.of(
                new RecordingFactory("first", calls), new RecordingFactory("second", calls)));
        Assertions.assertEquals(List.of("first:" + TaskStub.class.getSimpleName(),
                "second:" + Decorator.class.getSimpleName()), calls);
    }

    /**
     * Wraps every invoker in a {@link Decorator} and records which invoker it got.
     *
     * @param name the name of the factory
     * @param calls receives {@code <name>:<class of the invoker>} per call
     */
    private record RecordingFactory(String name, List<String> calls) implements InvokerDecoratorFactory {

        @Override
        public <T extends FlowNode> Invoker<T> decorate(final Invoker<T> invoker) {
            this.calls.add(this.name + ":" + invoker.getClass().getSimpleName());
            return new Decorator<>(invoker);
        }
    }

    /**
     * Passes every node on to its delegate.
     *
     * @param <T> the type of flow node
     * @param delegate the decorated invoker
     */
    private record Decorator<T extends FlowNode>(Invoker<T> delegate) implements InvokerDecorator<T> {

        @Override
        public List<SequenceFlow> invoke(final T node, final NodeExecution execution) throws ProcessEngineException {
            return this.delegate.invoke(node, execution);
        }
    }

    /**
     * An invoker of plain tasks that takes no flow.
     */
    private static final class TaskStub implements Invoker<Task> {

        @Override
        public Class<Task> nodeType() {
            return Task.class;
        }

        @Override
        public List<SequenceFlow> invoke(final Task node, final NodeExecution execution) {
            return List.of();
        }
    }
}
