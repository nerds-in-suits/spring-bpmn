package de.nemeses.spring.bpmn.exec.task;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import de.nemeses.spring.bpmn.exec.FlowSelector;
import de.nemeses.spring.bpmn.exec.Invoker;
import de.nemeses.spring.bpmn.exec.NodeExecution;
import de.nemeses.spring.bpmn.exec.ProcessEngineException;
import de.nemeses.spring.bpmn.model.bpmn20.ScriptTask;
import de.nemeses.spring.bpmn.model.bpmn20.SequenceFlow;

/**
 * Executes a {@code scriptTask}: runs its SpEL script with the {@link SpelScriptRunner} (once, or per iteration of its
 * standard loop, through the {@link TaskRunner}), stores the variables it changed, completes the step and takes the
 * outgoing flows like for every activity ({@link FlowSelector#activity}), evaluating their conditions against the
 * changed variables.
 */
@Component
class ScriptTaskInvoker implements Invoker<ScriptTask> {

    private final TaskRunner runner;
    private final FlowSelector flows;
    private final SpelScriptRunner scripts;

    ScriptTaskInvoker(final TaskRunner runner, final FlowSelector flows, final SpelScriptRunner scripts) {
        this.runner = runner;
        this.flows = flows;
        this.scripts = scripts;
    }

    @Override
    public Class<ScriptTask> nodeType() {
        return ScriptTask.class;
    }

    @Override
    public List<SequenceFlow> invoke(final ScriptTask node, final NodeExecution execution)
            throws ProcessEngineException {
        final Map<String, Object> variables = this.runner.run(node, execution,
                context -> this.scripts.execute(node, context));
        final List<SequenceFlow> taken = this.flows.activity(node, execution.outgoing(), execution.language(),
                variables);
        execution.complete();
        return taken;
    }
}
