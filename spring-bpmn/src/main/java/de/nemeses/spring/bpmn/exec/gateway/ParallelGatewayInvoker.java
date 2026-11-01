package de.nemeses.spring.bpmn.exec.gateway;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import de.nemeses.spring.bpmn.exec.FlowSelector;
import de.nemeses.spring.bpmn.exec.Invoker;
import de.nemeses.spring.bpmn.exec.NodeExecution;
import de.nemeses.spring.bpmn.exec.ProcessEngineException;
import de.nemeses.spring.bpmn.model.bpmn20.ParallelGateway;
import de.nemeses.spring.bpmn.model.bpmn20.SequenceFlow;
import de.nemeses.spring.bpmn.repository.ProcessStep;

/**
 * Executes a {@code parallelGateway}. With at most one incoming flow (fork) it completes the step and takes all
 * outgoing flows. With more incoming flows (join) it continues only if a step is pending at the gateway (in the same
 * scope) for every incoming flow: then the oldest pending step of each incoming flow is completed and all outgoing flows
 * are taken once; otherwise the step stays pending (parked at the join) and no flow is taken. The outgoing flows must
 * not have a condition.
 */
@Component
class ParallelGatewayInvoker implements Invoker<ParallelGateway> {

    @Override
    public Class<ParallelGateway> nodeType() {
        return ParallelGateway.class;
    }

    @Override
    public Collection<SequenceFlow> invoke(final ParallelGateway node, final NodeExecution execution)
            throws ProcessEngineException {
        final Collection<SequenceFlow> incoming = execution.incoming();
        if (incoming.size() <= 1) {
            final Collection<SequenceFlow> taken = FlowSelector.unconditional(execution.outgoing());
            execution.complete();
            return taken;
        }
        final Collection<UUID> joined = new ArrayList<>();
        for (final SequenceFlow flow : incoming) {
            final UUID oldest = execution.pendingInScope().stream()
                    .filter(pending -> flow.getId().equals(pending.incomingFlowId()))
                    .map(ProcessStep::id)
                    .findFirst()
                    .orElse(null);
            if (oldest == null) {
                LoggerFactory.getLogger(this.getClass()).debug(
                        "Parallel gateway {} of process instance {} waits for flow {}",
                        node.getId(), execution.instance().id(), flow.getId());
                return List.of();
            }
            joined.add(oldest);
        }
        final Collection<SequenceFlow> taken = FlowSelector.unconditional(execution.outgoing());
        execution.complete(joined);
        return taken;
    }
}
