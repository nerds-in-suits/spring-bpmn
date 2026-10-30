package de.nemeses.spring.bpmn;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import de.nemeses.spring.bpmn.model.bpmn20.FlowElement;
import de.nemeses.spring.bpmn.model.bpmn20.FlowNode;
import de.nemeses.spring.bpmn.model.bpmn20.Process;
import de.nemeses.spring.bpmn.model.bpmn20.SequenceFlow;
import de.nemeses.spring.bpmn.model.bpmn20.SubProcess;
import de.nemeses.spring.bpmn.repository.NoSuchProcessInstanceException;
import de.nemeses.spring.bpmn.repository.NoSuchProcessStepException;
import de.nemeses.spring.bpmn.repository.ProcessInstance;
import de.nemeses.spring.bpmn.repository.ProcessInstanceRepository;
import de.nemeses.spring.bpmn.repository.ProcessStep;
import de.nemeses.spring.bpmn.repository.ProcessStepRepository;
import de.nemeses.spring.tasks.TaskService;

/**
 * Creates the steps for the flows taken in their scope and enters and leaves embedded sub-processes for the
 * {@link BpmnNodeExecutor}. The scope of a step is the step of the
 * sub-process it runs in ({@link ProcessStep#parentStepId()}, {@code null} at the top level of the process). The
 * step of a sub-process stays pending while the sub-process runs and is completed when none of its child steps is
 * pending any more.
 * <p>
 * The scope is checked after every step ({@link #leaveCompleted}), so nested sub-processes and parallel paths inside
 * one work, and two tokens in the same sub-process (two pending sub-process steps) are kept apart; a join only joins
 * the pending steps of its own scope ({@link #inSameScope}). Like a task without outgoing flow, a top-level
 * sub-process without outgoing flow does not end the instance; only a top-level end event does.
 */
@Component
class SubProcessScopes {

    private final ProcessInstanceRepository instances;
    private final ProcessStepRepository steps;
    private final TaskService taskService;
    private final FlowSelector flows;

    SubProcessScopes(final ProcessInstanceRepository instances, final ProcessStepRepository steps,
            final TaskService taskService, final FlowSelector flows) {
        this.instances = instances;
        this.steps = steps;
        this.taskService = taskService;
        this.flows = flows;
    }

    /**
     * @param step a step
     * @param other another step
     * @return whether both steps run in the same scope (the same sub-process step, or both at the top level)
     */
    static boolean inSameScope(final ProcessStep step, final ProcessStep other) {
        return Objects.equals(step.parentStepId(), other.parentStepId());
    }

    /**
     * Creates a step in a scope and triggers a {@link BpmnNodeTask} for the target of each flow.
     *
     * @param instance the process instance
     * @param taken the sequence flows taken
     * @param parentStepId the scope of the new steps: the step of the embedded sub-process the targets run in,
     *            {@code null} at the top level
     * @throws NoSuchProcessInstanceException if the instance does not exist
     */
    void take(final ProcessInstance instance, final List<SequenceFlow> taken, final UUID parentStepId)
            throws NoSuchProcessInstanceException {
        for (final SequenceFlow flow : taken) {
            BpmnNodeExecutor.schedule(this.steps, this.taskService, instance.id(),
                    ((FlowElement) flow.getTargetRef()).getId(), flow.getId(), parentStepId);
        }
    }

    /**
     * Enters an embedded sub-process: creates a step (with the sub-process step as parent) and triggers a
     * {@link BpmnNodeTask} for each start event directly inside it. The sub-process step stays pending. If the
     * sub-process step already has child steps (a task delivered again), nothing is done.
     *
     * @param instance the process instance
     * @param step the step of the node
     * @param node the node
     * @return {@code false} if the node is no sub-process (and nothing was done)
     * @throws ProcessEngineException if the sub-process is not supported (event sub-process, transaction, ad-hoc
     *             sub-process, loop characteristics) or has no start event
     * @throws NoSuchProcessInstanceException if the instance does not exist
     */
    boolean enter(final ProcessInstance instance, final ProcessStep step, final FlowNode node)
            throws ProcessEngineException, NoSuchProcessInstanceException {
        if (!(node instanceof final SubProcess subProcess)) {
            return false;
        }
        if (subProcess.getClass() != SubProcess.class || subProcess.isTriggeredByEvent()) {
            throw new ProcessEngineException("Node '" + subProcess.getId() + "' is a "
                    + (subProcess.isTriggeredByEvent() ? "event sub-process" : subProcess.getClass().getSimpleName())
                    + ", which is not supported yet.");
        }
        if (subProcess.getLoopCharacteristics() != null) {
            throw new ProcessEngineException("Activity '" + subProcess.getId()
                    + "' has loop characteristics, which are not supported yet.");
        }
        if (!this.steps.children(step.id()).isEmpty()) {
            LoggerFactory.getLogger(this.getClass()).info(
                    "Skipping step {} (sub-process {}) of process instance {}, which has already been entered.",
                    step.id(), subProcess.getId(), instance.id());
            return true;
        }
        final List<String> startEvents = ProcessNavigator.startEvents(subProcess);
        if (startEvents.isEmpty()) {
            throw new ProcessEngineException("Sub-process '" + subProcess.getId() + "' has no start event.");
        }
        for (final String startEvent : startEvents) {
            BpmnNodeExecutor.schedule(this.steps, this.taskService, instance.id(), startEvent, null, step.id());
        }
        return true;
    }

    /**
     * Completes the sub-process step {@code parentStepId} if none of its child steps is pending any more and takes
     * the outgoing flows of the sub-process like for every activity (evaluating their conditions against the current
     * variables). If it takes no flow, the enclosing sub-process is checked the same way, up to the top level.
     *
     * @param instance the process instance
     * @param language the default expression language of the document
     * @param process the process
     * @param parentStepId the scope of the step just executed, {@code null} at the top level
     * @throws ProcessEngineException if a condition cannot be evaluated
     * @throws NoSuchProcessStepException if the sub-process step does not exist
     * @throws NoSuchProcessInstanceException if the instance does not exist
     */
    void leaveCompleted(final ProcessInstance instance, final String language, final Process process,
            final UUID parentStepId)
            throws ProcessEngineException, NoSuchProcessStepException, NoSuchProcessInstanceException {
        UUID scope = parentStepId;
        while (scope != null) {
            if (this.steps.children(scope).stream().anyMatch(child -> child.endDate() == null)) {
                return;
            }
            final ProcessStep subProcessStep = this.steps.retrieve(scope);
            final SubProcess subProcess = (SubProcess) ProcessNavigator.flowNode(process, subProcessStep.nodeId());
            // the variables as changed by the step just executed
            final Map<String, Object> variables = this.instances.retrieve(instance.id()).variables();
            final List<SequenceFlow> taken = this.flows.activity(subProcess,
                    ProcessNavigator.outgoing(process, subProcess), language, variables);
            this.steps.complete(List.of(scope), new Date());
            this.take(instance, taken, subProcessStep.parentStepId());
            LoggerFactory.getLogger(this.getClass()).debug(
                    "Completed sub-process {} (step {}) of process instance {}, taken flows {}",
                    subProcess.getId(), scope, instance.id(), taken.stream().map(SequenceFlow::getId).toList());
            if (!taken.isEmpty()) {
                return;
            }
            scope = subProcessStep.parentStepId();
        }
    }
}
