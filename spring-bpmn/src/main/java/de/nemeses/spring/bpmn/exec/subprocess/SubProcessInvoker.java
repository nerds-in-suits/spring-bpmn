package de.nemeses.spring.bpmn.exec.subprocess;

import java.util.Collection;
import java.util.List;

import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import de.nemeses.spring.bpmn.exec.BpmnNodeExecutor;
import de.nemeses.spring.bpmn.exec.FlowSelector;
import de.nemeses.spring.bpmn.exec.Invoker;
import de.nemeses.spring.bpmn.exec.NodeExecution;
import de.nemeses.spring.bpmn.exec.NodeTrigger;
import de.nemeses.spring.bpmn.exec.ProcessEngineException;
import de.nemeses.spring.bpmn.model.bpmn20.FlowElement;
import de.nemeses.spring.bpmn.model.bpmn20.SequenceFlow;
import de.nemeses.spring.bpmn.model.bpmn20.StartEvent;
import de.nemeses.spring.bpmn.model.bpmn20.SubProcess;
import de.nemeses.spring.bpmn.repository.ProcessStep;
import de.nemeses.spring.bpmn.repository.ProcessStepRepository;
import jakarta.xml.bind.JAXBElement;

/**
 * Enters and leaves an embedded {@code subProcess}. Reaching it creates the sub-process step, without task, and
 * triggers each start event directly inside it with the sub-process step as scope ({@link NodeTrigger#trigger}); the
 * sub-process step stays pending. Once none of its child steps is pending any more, the {@link BpmnNodeExecutor}
 * schedules a task for it, whose execution completes it and takes the outgoing flows like for every activity
 * ({@link FlowSelector#activity}), evaluating their conditions against the variables as changed inside. An execution
 * while a child step is pending does nothing. Event sub-processes, sub-processes without start event and loop
 * characteristics are not supported and fail when the sub-process is reached (transactions and ad-hoc sub-processes
 * are other node types).
 */
@Component
class SubProcessInvoker implements Invoker<SubProcess> {

    private final ProcessStepRepository steps;
    private final FlowSelector flows;

    SubProcessInvoker(final ProcessStepRepository steps, final FlowSelector flows) {
        this.steps = steps;
        this.flows = flows;
    }

    @Override
    public Class<SubProcess> nodeType() {
        return SubProcess.class;
    }

    @Override
    public ProcessStep trigger(final SubProcess node, final NodeTrigger trigger) throws ProcessEngineException {
        if (node.isTriggeredByEvent()) {
            throw new ProcessEngineException("Node '" + node.getId()
                    + "' is a event sub-process, which is not supported yet.");
        }
        if (node.getLoopCharacteristics() != null) {
            throw new ProcessEngineException("Activity '" + node.getId()
                    + "' has loop characteristics, which are not supported yet.");
        }
        final Collection<String> startEvents = SubProcessInvoker.startEvents(node);
        if (startEvents.isEmpty()) {
            throw new ProcessEngineException("Sub-process '" + node.getId() + "' has no start event.");
        }
        final ProcessStep step = trigger.createStep();
        for (final String startEvent : startEvents) {
            trigger.trigger(startEvent, null, step.id());
        }
        return step;
    }

    @Override
    public Collection<SequenceFlow> execute(final SubProcess node, final NodeExecution execution)
            throws ProcessEngineException {
        final ProcessStep step = execution.step();
        if (this.steps.children(step.id()).stream().anyMatch(child -> child.endDate() == null)) {
            LoggerFactory.getLogger(this.getClass()).info(
                    "Skipping step {} (sub-process {}) of process instance {}, which still runs.",
                    step.id(), node.getId(), execution.instance().id());
            return List.of();
        }
        final Collection<SequenceFlow> taken = this.flows.activity(node, execution.outgoing(), execution.language(),
                execution.instance().variables());
        execution.complete();
        LoggerFactory.getLogger(this.getClass()).debug(
                "Completed sub-process {} (step {}) of process instance {}, taken flows {}",
                node.getId(), step.id(), execution.instance().id(), taken.stream().map(SequenceFlow::getId).toList());
        return taken;
    }

    /**
     * @return the ids of the start events directly inside the sub-process (not in nested ones)
     */
    private static Collection<String> startEvents(final SubProcess subProcess) {
        return subProcess.getFlowElement().stream()
                .map(JAXBElement::getValue)
                .filter(StartEvent.class::isInstance)
                .map(FlowElement::getId)
                .toList();
    }
}
