package de.nemeses.spring.bpmn.exec.task;

import java.util.Collection;
import java.util.Map;

import org.springframework.stereotype.Component;

import de.nemeses.spring.bpmn.exec.FlowSelector;
import de.nemeses.spring.bpmn.exec.Invoker;
import de.nemeses.spring.bpmn.exec.NodeExecution;
import de.nemeses.spring.bpmn.exec.ProcessEngineException;
import de.nemeses.spring.bpmn.model.bpmn20.SequenceFlow;
import de.nemeses.spring.bpmn.model.bpmn20.Task;

/**
 * Executes a plain {@code task}: it has no work, so only its standard loop (if any) runs, evaluating the loop
 * condition; then completes the step and takes the outgoing flows like for every activity
 * ({@link FlowSelector#activity}).
 */
@Component
class TaskInvoker implements Invoker<Task> {

    private final TaskRunner runner;
    private final FlowSelector flows;

    TaskInvoker(final TaskRunner runner, final FlowSelector flows) {
        this.runner = runner;
        this.flows = flows;
    }

    @Override
    public Class<Task> nodeType() {
        return Task.class;
    }

    @Override
    public Collection<SequenceFlow> invoke(final Task node, final NodeExecution execution)
            throws ProcessEngineException {
        final Map<String, Object> variables = this.runner.loop(node, execution);
        final Collection<SequenceFlow> taken = this.flows.activity(node, execution.outgoing(), execution.language(),
                variables);
        execution.complete();
        return taken;
    }
}
