package de.nemeses.spring.bpmn.exec.subprocess;

import java.util.Collection;
import java.util.List;

import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import de.nemeses.spring.bpmn.exec.BpmnNodeExecutor;
import de.nemeses.spring.bpmn.exec.BpmnNodeTask;
import de.nemeses.spring.bpmn.exec.Invoker;
import de.nemeses.spring.bpmn.exec.NodeExecution;
import de.nemeses.spring.bpmn.exec.ProcessEngineException;
import de.nemeses.spring.bpmn.model.bpmn20.FlowElement;
import de.nemeses.spring.bpmn.model.bpmn20.SequenceFlow;
import de.nemeses.spring.bpmn.model.bpmn20.StartEvent;
import de.nemeses.spring.bpmn.model.bpmn20.SubProcess;
import de.nemeses.spring.bpmn.repository.NoSuchProcessInstanceException;
import de.nemeses.spring.bpmn.repository.ProcessInstance;
import de.nemeses.spring.bpmn.repository.ProcessStep;
import de.nemeses.spring.bpmn.repository.ProcessStepRepository;
import de.nemeses.spring.tasks.TaskService;
import jakarta.xml.bind.JAXBElement;

/**
 * Enters an embedded {@code subProcess}: creates a step (with the sub-process step as parent) and triggers a
 * {@link BpmnNodeTask} for each start event directly inside it. The sub-process step stays pending and no flow is
 * taken; the {@link BpmnNodeExecutor} completes it and takes its outgoing flows when none of its child steps is pending
 * any more. If the sub-process step already has child steps (a task delivered again), nothing is done. Event
 * sub-processes and loop characteristics are not supported (transactions and ad-hoc sub-processes are other node
 * types).
 */
@Component
class SubProcessInvoker implements Invoker<SubProcess> {

    private final ProcessStepRepository steps;
    private final TaskService taskService;

    SubProcessInvoker(final ProcessStepRepository steps, final TaskService taskService) {
        this.steps = steps;
        this.taskService = taskService;
    }

    @Override
    public Class<SubProcess> nodeType() {
        return SubProcess.class;
    }

    @Override
    public Collection<SequenceFlow> invoke(final SubProcess node, final NodeExecution execution)
            throws ProcessEngineException {
        if (node.isTriggeredByEvent()) {
            throw new ProcessEngineException("Node '" + node.getId()
                    + "' is a event sub-process, which is not supported yet.");
        }
        if (node.getLoopCharacteristics() != null) {
            throw new ProcessEngineException("Activity '" + node.getId()
                    + "' has loop characteristics, which are not supported yet.");
        }
        final ProcessInstance instance = execution.instance();
        final ProcessStep step = execution.step();
        if (!this.steps.children(step.id()).isEmpty()) {
            LoggerFactory.getLogger(this.getClass()).info(
                    "Skipping step {} (sub-process {}) of process instance {}, which has already been entered.",
                    step.id(), node.getId(), instance.id());
            return List.of();
        }
        final Collection<String> startEvents = SubProcessInvoker.startEvents(node);
        if (startEvents.isEmpty()) {
            throw new ProcessEngineException("Sub-process '" + node.getId() + "' has no start event.");
        }
        try {
            for (final String startEvent : startEvents) {
                BpmnNodeExecutor.schedule(this.steps, this.taskService, instance.id(), startEvent, null, step.id());
            }
        } catch (final NoSuchProcessInstanceException e) {
            throw new ProcessEngineException("Cannot enter sub-process '" + node.getId() + "' of process instance "
                    + instance.id() + ".", e);
        }
        return List.of();
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
