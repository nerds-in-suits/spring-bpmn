package de.nemeses.spring.bpmn.exec.event;

import java.util.Date;
import java.util.List;

import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import de.nemeses.spring.bpmn.exec.BpmnNodeTask;
import de.nemeses.spring.bpmn.exec.Invoker;
import de.nemeses.spring.bpmn.exec.NodeExecution;
import de.nemeses.spring.bpmn.exec.ProcessEngineException;
import de.nemeses.spring.bpmn.model.bpmn20.EndEvent;
import de.nemeses.spring.bpmn.model.bpmn20.SequenceFlow;
import de.nemeses.spring.bpmn.repository.NoSuchProcessInstanceException;
import de.nemeses.spring.bpmn.repository.ProcessInstance;
import de.nemeses.spring.bpmn.repository.ProcessInstanceRepository;
import de.nemeses.spring.bpmn.repository.ProcessStep;
import de.nemeses.spring.bpmn.repository.ProcessStepRepository;
import de.nemeses.spring.tasks.TaskService;

/**
 * Executes an {@code endEvent} without event definition: completes the step and, at the top level of the process,
 * ends the instance (sets its end date) if no other step of it is pending. If the ended instance was started by a
 * {@code callActivity}, triggers a {@link BpmnNodeTask} for the waiting call activity step of the parent instance; the
 * step runs in its own task, which locks the parent instance. Inside a sub-process the end event only completes its
 * step.
 */
@Component
class EndEventInvoker implements Invoker<EndEvent> {

    private final ProcessInstanceRepository instances;
    private final ProcessStepRepository steps;
    private final TaskService taskService;

    EndEventInvoker(final ProcessInstanceRepository instances, final ProcessStepRepository steps,
            final TaskService taskService) {
        this.instances = instances;
        this.steps = steps;
        this.taskService = taskService;
    }

    @Override
    public Class<EndEvent> nodeType() {
        return EndEvent.class;
    }

    @Override
    public List<SequenceFlow> invoke(final EndEvent node, final NodeExecution execution)
            throws ProcessEngineException {
        if (!node.getEventDefinition().isEmpty() || !node.getEventDefinitionRef().isEmpty()) {
            throw new ProcessEngineException("End event '" + node.getId()
                    + "' has an event definition, which is not supported yet.");
        }
        execution.complete();
        if (execution.step().parentStepId() == null) {
            this.end(execution.instance(), node);
        }
        return List.of();
    }

    /**
     * Ends the instance if none of its steps is pending any more.
     */
    private void end(final ProcessInstance instance, final EndEvent event) throws ProcessEngineException {
        final long pending = this.steps.countPending(instance.id());
        if (pending > 0) {
            LoggerFactory.getLogger(this.getClass()).debug(
                    "Reached end event {} of process instance {}, which still has {} pending steps",
                    event.getId(), instance.id(), pending);
            return;
        }
        try {
            this.instances.updateEndDate(instance.id(), new Date());
        } catch (final NoSuchProcessInstanceException e) {
            throw new ProcessEngineException("Cannot end process instance " + instance.id() + ".", e);
        }
        LoggerFactory.getLogger(this.getClass()).info("Process instance {} ended at {}", instance.id(), event.getId());
        final ProcessStep parentStep = instance.parentStep();
        if (parentStep != null) {
            this.taskService.trigger(BpmnNodeTask.BEAN_NAME, parentStep.id());
        }
    }
}
