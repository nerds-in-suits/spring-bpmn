package de.nemeses.spring.bpmn.exec.gateway;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.slf4j.LoggerFactory;

import de.nemeses.spring.bpmn.exec.FlowSelector;
import de.nemeses.spring.bpmn.exec.Invoker;
import de.nemeses.spring.bpmn.exec.NodeExecution;
import de.nemeses.spring.bpmn.exec.NodeTrigger;
import de.nemeses.spring.bpmn.exec.ProcessEngineException;
import de.nemeses.spring.bpmn.model.bpmn20.ParallelGateway;
import de.nemeses.spring.bpmn.model.bpmn20.SequenceFlow;
import de.nemeses.spring.bpmn.repository.ProcessStep;

/**
 * Triggers and executes a {@code parallelGateway}. With at most one incoming flow (fork) it is reached like any node
 * and its execution completes the step and takes all outgoing flows. With more incoming flows (join), reaching it
 * creates a step, which is parked there without task unless a step is then pending at the gateway (in the same scope)
 * for every incoming flow; only then a {@code bpmnNodeTask} is scheduled for the new step. Its execution completes the
 * oldest pending step of each incoming flow and takes all outgoing flows once; if the set is no longer complete (a
 * task delivered again, or a set already joined by another step), the step stays pending and no flow is taken. The
 * outgoing flows must not have a condition.
 */
class ParallelGatewayInvoker implements Invoker<ParallelGateway> {

    @Override
    public Class<ParallelGateway> nodeType() {
        return ParallelGateway.class;
    }

    @Override
    public ProcessStep trigger(final ParallelGateway node, final NodeTrigger trigger) throws ProcessEngineException {
        final ProcessStep step = trigger.createStep();
        final Collection<SequenceFlow> incoming = trigger.incoming();
        if (incoming.size() <= 1 || !ParallelGatewayInvoker.joined(incoming, trigger.pendingInScope()).isEmpty()) {
            return trigger.schedule(step);
        }
        LoggerFactory.getLogger(this.getClass()).debug(
                "Parallel gateway {} parks step {} until a step arrived on every incoming flow",
                node.getId(), step.id());
        return step;
    }

    @Override
    public Collection<SequenceFlow> execute(final ParallelGateway node, final NodeExecution execution)
            throws ProcessEngineException {
        final Collection<SequenceFlow> incoming = execution.incoming();
        if (incoming.size() <= 1) {
            final Collection<SequenceFlow> taken = FlowSelector.unconditional(execution.outgoing());
            execution.complete();
            return taken;
        }
        final Collection<UUID> joined = ParallelGatewayInvoker.joined(incoming, execution.pendingInScope());
        if (joined.isEmpty()) {
            LoggerFactory.getLogger(this.getClass()).debug(
                    "Parallel gateway {} of process instance {} still waits for a step on every incoming flow",
                    node.getId(), execution.instance().id());
            return List.of();
        }
        final Collection<SequenceFlow> taken = FlowSelector.unconditional(execution.outgoing());
        execution.complete(joined);
        return taken;
    }

    /**
     * @param incoming the incoming flows of the join
     * @param pending the pending steps at the join in one scope, oldest first
     * @return the ids of the oldest pending step of each incoming flow, none if a flow has none
     */
    private static Collection<UUID> joined(final Collection<SequenceFlow> incoming,
            final List<ProcessStep> pending) {
        final Collection<UUID> joined = new ArrayList<>();
        for (final SequenceFlow flow : incoming) {
            final UUID oldest = pending.stream()
                    .filter(step -> flow.getId().equals(step.incomingFlowId()))
                    .map(ProcessStep::id)
                    .findFirst()
                    .orElse(null);
            if (oldest == null) {
                return List.of();
            }
            joined.add(oldest);
        }
        return joined;
    }
}
