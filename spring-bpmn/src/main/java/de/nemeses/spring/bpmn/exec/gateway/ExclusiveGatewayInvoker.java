package de.nemeses.spring.bpmn.exec.gateway;

import java.util.List;

import org.springframework.stereotype.Component;

import de.nemeses.spring.bpmn.exec.FlowSelector;
import de.nemeses.spring.bpmn.exec.Invoker;
import de.nemeses.spring.bpmn.exec.NodeExecution;
import de.nemeses.spring.bpmn.exec.ProcessEngineException;
import de.nemeses.spring.bpmn.model.bpmn20.ExclusiveGateway;
import de.nemeses.spring.bpmn.model.bpmn20.SequenceFlow;

/**
 * Executes an {@code exclusiveGateway}: completes the step and takes the first non-default outgoing flow (in document
 * order) without condition or whose condition is true, else the {@code default} flow ({@link FlowSelector#exclusive});
 * fails if there is none.
 */
@Component
class ExclusiveGatewayInvoker implements Invoker<ExclusiveGateway> {

    private final FlowSelector flows;

    ExclusiveGatewayInvoker(final FlowSelector flows) {
        this.flows = flows;
    }

    @Override
    public Class<ExclusiveGateway> nodeType() {
        return ExclusiveGateway.class;
    }

    @Override
    public List<SequenceFlow> invoke(final ExclusiveGateway node, final NodeExecution execution)
            throws ProcessEngineException {
        final SequenceFlow taken = this.flows.exclusive(node.getId(), node.getDefault(), execution.outgoing(),
                execution.language(), execution.instance().variables());
        execution.complete();
        return List.of(taken);
    }
}
