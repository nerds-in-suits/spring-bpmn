package de.nemeses.spring.bpmn.exec.task;

import java.util.List;
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
 * ({@link FlowSelector#activity}). A multi-instance loop runs through the {@link MultiInstanceLoop}, with one step
 * per instance.
 */
@Component
class TaskInvoker implements Invoker<Task> {

    private final TaskRunner runner;
    private final FlowSelector flows;
    private final MultiInstanceLoop multiInstances;

    TaskInvoker(final TaskRunner runner, final FlowSelector flows, final MultiInstanceLoop multiInstances) {
        this.runner = runner;
        this.flows = flows;
        this.multiInstances = multiInstances;
    }

    @Override
    public Class<Task> nodeType() {
        return Task.class;
    }

    @Override
    public List<SequenceFlow> invoke(final Task node, final NodeExecution execution) throws ProcessEngineException {
        if (MultiInstanceLoop.isMultiInstance(node)) {
            return this.multiInstances.execute(node, execution, _ -> {
                // a plain task has no work
            });
        }
        final Map<String, Object> variables = this.runner.loop(node, execution);
        final List<SequenceFlow> taken = this.flows.activity(node, execution.outgoing(), execution.language(),
                variables);
        execution.complete();
        return taken;
    }
}
