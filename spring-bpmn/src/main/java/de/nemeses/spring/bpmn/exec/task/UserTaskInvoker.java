package de.nemeses.spring.bpmn.exec.task;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.LoggerFactory;

import de.nemeses.spring.bpmn.exec.BpmnNodeExecutor;
import de.nemeses.spring.bpmn.exec.FlowSelector;
import de.nemeses.spring.bpmn.exec.Invoker;
import de.nemeses.spring.bpmn.exec.NodeExecution;
import de.nemeses.spring.bpmn.exec.NodeTrigger;
import de.nemeses.spring.bpmn.exec.ProcessEngineException;
import de.nemeses.spring.bpmn.exec.ProcessEngineService;
import de.nemeses.spring.bpmn.model.bpmn20.SequenceFlow;
import de.nemeses.spring.bpmn.model.bpmn20.UserTask;
import de.nemeses.spring.bpmn.repository.NoSuchProcessInstanceException;
import de.nemeses.spring.bpmn.repository.ProcessInstance;
import de.nemeses.spring.bpmn.repository.ProcessInstanceRepository;
import de.nemeses.spring.bpmn.repository.ProcessStep;

/**
 * Triggers and executes a {@code userTask}. A user task is a wait state: reaching it only creates its step, without
 * scheduling a task. The pending step is the open user task (instance, node and creation date are those of the
 * {@code ProcessStep}), so it needs no store of its own, and it keeps the instance running.
 * <p>
 * {@link ProcessEngineService#completeUserTask} executes the step from outside
 * ({@link BpmnNodeExecutor#completeUserTask}): the given variables ({@link NodeExecution#completion()}) are added to
 * the process variables or replace them (none is removed) and are stored if they change any, then the step is
 * completed and the outgoing flows are taken like for every activity ({@link FlowSelector#activity}), evaluating their
 * conditions against the changed variables.
 * <p>
 * Loop characteristics fail when the user task is reached, which fails the triggering step (#100).
 */
class UserTaskInvoker implements Invoker<UserTask> {

    private final ProcessInstanceRepository instances;
    private final FlowSelector flows;

    UserTaskInvoker(final ProcessInstanceRepository instances, final FlowSelector flows) {
        this.instances = instances;
        this.flows = flows;
    }

    @Override
    public Class<UserTask> nodeType() {
        return UserTask.class;
    }

    /**
     * Creates the pending step of the user task; schedules no task.
     *
     * @param node the user task
     * @param trigger how the user task is reached
     * @return the pending step, the open user task
     * @throws ProcessEngineException if the user task has loop characteristics or the step cannot be created
     */
    @Override
    public ProcessStep trigger(final UserTask node, final NodeTrigger trigger) throws ProcessEngineException {
        if (node.getLoopCharacteristics() != null) {
            throw new ProcessEngineException("User task '" + node.getId()
                    + "' has loop characteristics, which are not supported yet.");
        }
        final ProcessStep step = trigger.createStep();
        LoggerFactory.getLogger(this.getClass()).debug("User task {} of process instance {} waits in step {}",
                node.getId(), step.processInstanceId(), step.id());
        return step;
    }

    /**
     * Completes the user task with the variables passed from outside and selects the outgoing flows.
     *
     * @param node the user task
     * @param execution the pending user task step with its completion variables
     * @return the sequence flows to take
     * @throws ProcessEngineException if the execution has no completion variables (it was not started by
     *             {@link BpmnNodeExecutor#completeUserTask}) or a flow condition fails
     */
    @Override
    public Collection<SequenceFlow> execute(final UserTask node, final NodeExecution execution)
            throws ProcessEngineException {
        final ProcessInstance instance = execution.instance();
        try {
            final Map<String, Object> completion = execution.completion().orElseThrow(
                    () -> new ProcessEngineException("Step " + execution.step().id() + " of user task '"
                            + node.getId() + "' can only be completed through completeUserTask."));
            final Map<String, Object> variables = new LinkedHashMap<>(instance.variables());
            variables.putAll(completion);
            if (!variables.equals(instance.variables())) {
                this.instances.updateVariables(instance.id(), variables);
            }
            execution.complete();
            LoggerFactory.getLogger(this.getClass()).info("Completed user task {} (step {}) of process instance {}",
                    node.getId(), execution.step().id(), instance.id());
            return this.flows.activity(node, execution.outgoing(), execution.language(), variables);
        } catch (final NoSuchProcessInstanceException e) {
            throw new ProcessEngineException("Cannot complete user task '" + node.getId() + "' of process instance "
                    + instance.id() + ".", e);
        }
    }
}
