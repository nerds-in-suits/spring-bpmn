package de.nemeses.spring.bpmn.exec.task;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import de.nemeses.spring.bpmn.exec.FlowSelector;
import de.nemeses.spring.bpmn.exec.Invoker;
import de.nemeses.spring.bpmn.exec.NodeExecution;
import de.nemeses.spring.bpmn.exec.ProcessEngineException;
import de.nemeses.spring.bpmn.model.bpmn20.ManualTask;
import de.nemeses.spring.bpmn.model.bpmn20.SequenceFlow;

/**
 * Executes a {@code manualTask} like a plain {@code task} ({@link TaskInvoker}): the manual work happens outside the
 * engine and is not tracked, so the task does not wait but passes through; only its standard loop (if any) runs,
 * evaluating the loop condition; then it completes the step and takes the outgoing flows like for every activity
 * ({@link FlowSelector#activity}).
 */
@Component
class ManualTaskInvoker implements Invoker<ManualTask> {

    private final TaskRunner runner;
    private final FlowSelector flows;

    ManualTaskInvoker(final TaskRunner runner, final FlowSelector flows) {
        this.runner = runner;
        this.flows = flows;
    }

    @Override
    public Class<ManualTask> nodeType() {
        return ManualTask.class;
    }

    @Override
    public List<SequenceFlow> invoke(final ManualTask node, final NodeExecution execution)
            throws ProcessEngineException {
        final Map<String, Object> variables = this.runner.loop(node, execution);
        final List<SequenceFlow> taken = this.flows.activity(node, execution.outgoing(), execution.language(),
                variables);
        execution.complete();
        return taken;
    }
}
