package de.nemeses.spring.bpmn.exec;

import java.util.Collection;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.LoggerFactory;

import de.nemeses.spring.bpmn.model.bpmn20.FlowElement;
import de.nemeses.spring.bpmn.model.bpmn20.Process;
import de.nemeses.spring.bpmn.model.bpmn20.SequenceFlow;
import de.nemeses.spring.bpmn.repository.NoSuchProcessStepException;
import de.nemeses.spring.bpmn.repository.ProcessStep;
import de.nemeses.spring.bpmn.repository.ProcessStepRepository;
import de.nemeses.spring.tasks.TaskService;

/**
 * Triggers the targets of the flows taken in their scope and schedules leaving embedded sub-processes for the
 * {@link BpmnNodeExecutor} (they are entered and left by the {@link Invoker} of {@code subProcess}). The scope of a
 * step is the step of the sub-process it runs in ({@link ProcessStep#parentStepId()}, {@code null} at the top level
 * of the process). The step of a sub-process stays pending while the sub-process runs.
 * <p>
 * The scope is checked after every step ({@link #leaveCompleted}): once none of its child steps is pending any more,
 * a {@value BpmnNodeExecutor#BEAN_NAME} task is triggered for the sub-process step, which then completes it and takes
 * its outgoing flows. So nested sub-processes are left one task after the other, parallel paths inside one work, and
 * two tokens in the same sub-process (two pending sub-process steps) are kept apart; a join only joins the pending
 * steps of its own scope ({@link #inSameScope}). Like a task without outgoing flow, a top-level sub-process without
 * outgoing flow does not end the instance; only a top-level end event does.
 */
class SubProcessScopes {

    private final ProcessStepRepository steps;
    private final TaskService taskService;
    private final InvokerRegistry invokers;

    SubProcessScopes(final ProcessStepRepository steps, final TaskService taskService,
            final InvokerRegistry invokers) {
        this.steps = steps;
        this.taskService = taskService;
        this.invokers = invokers;
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
     * Triggers the target of each flow in a scope ({@link Invoker#trigger}).
     *
     * @param instanceId the id of the process instance
     * @param process the process
     * @param language the default expression language of the document
     * @param taken the sequence flows taken
     * @param parentStepId the scope of the targets: the step of the embedded sub-process they run in, {@code null} at
     *            the top level
     * @throws ProcessEngineException if a target is not supported or cannot be triggered
     */
    void take(final UUID instanceId, final Process process, final String language,
            final Collection<SequenceFlow> taken, final UUID parentStepId) throws ProcessEngineException {
        for (final SequenceFlow flow : taken) {
            this.invokers.trigger(instanceId, process, language,
                    ProcessNavigator.flowNode(process, ((FlowElement) flow.getTargetRef()).getId()), flow.getId(),
                    parentStepId);
        }
    }

    /**
     * Triggers a {@value BpmnNodeExecutor#BEAN_NAME} task for the pending sub-process step {@code parentStepId} if
     * none of its child steps is pending any more.
     *
     * @param instanceId the id of the process instance
     * @param parentStepId the scope of the step just executed, {@code null} at the top level
     * @throws NoSuchProcessStepException if the sub-process step does not exist
     */
    void leaveCompleted(final UUID instanceId, final UUID parentStepId) throws NoSuchProcessStepException {
        if (parentStepId == null
                || this.steps.children(parentStepId).stream().anyMatch(child -> child.endDate() == null)
                || this.steps.retrieve(parentStepId).endDate() != null) {
            return;
        }
        this.taskService.trigger(BpmnNodeExecutor.BEAN_NAME, parentStepId);
        LoggerFactory.getLogger(this.getClass()).debug(
                "Scheduled leaving the sub-process of step {} of process instance {}", parentStepId, instanceId);
    }
}
