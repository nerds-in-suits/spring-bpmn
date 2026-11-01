package de.nemeses.spring.bpmn.exec.event;

import java.util.List;

import org.springframework.stereotype.Component;

import de.nemeses.spring.bpmn.exec.FlowSelector;
import de.nemeses.spring.bpmn.exec.Invoker;
import de.nemeses.spring.bpmn.exec.NodeExecution;
import de.nemeses.spring.bpmn.exec.ProcessEngineException;
import de.nemeses.spring.bpmn.model.bpmn20.SequenceFlow;
import de.nemeses.spring.bpmn.model.bpmn20.StartEvent;

/**
 * Executes a {@code startEvent}: nothing to do, completes the step and takes all outgoing flows, which must not have a
 * condition.
 */
@Component
class StartEventInvoker implements Invoker<StartEvent> {

    @Override
    public Class<StartEvent> nodeType() {
        return StartEvent.class;
    }

    @Override
    public List<SequenceFlow> invoke(final StartEvent node, final NodeExecution execution)
            throws ProcessEngineException {
        final List<SequenceFlow> taken = FlowSelector.unconditional(execution.outgoing());
        execution.complete();
        return taken;
    }
}
