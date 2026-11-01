package de.nemeses.spring.bpmn.exec;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

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
                () -> new BpmnNodeExecutor(null, null, null, null, invokers));
        Assertions.assertTrue(e.getMessage().contains("Task"), e.getMessage());
    }

    @Test
    void testOneInvokerPerNodeType() {
        Assertions.assertDoesNotThrow(() -> new BpmnNodeExecutor(null, null, null, null, List.of(new TaskStub())));
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
