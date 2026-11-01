package de.nemeses.spring.bpmn.exec.task;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import de.nemeses.spring.bpmn.exec.BpmnNodeExecutor;
import de.nemeses.spring.bpmn.exec.FlowSelector;
import de.nemeses.spring.bpmn.exec.Invoker;
import de.nemeses.spring.bpmn.exec.NodeExecution;
import de.nemeses.spring.bpmn.exec.ProcessEngineException;
import de.nemeses.spring.bpmn.exec.ProcessEngineService;
import de.nemeses.spring.bpmn.model.bpmn20.SequenceFlow;
import de.nemeses.spring.bpmn.model.bpmn20.UserTask;
import de.nemeses.spring.bpmn.repository.NoSuchProcessInstanceException;
import de.nemeses.spring.bpmn.repository.ProcessInstance;
import de.nemeses.spring.bpmn.repository.ProcessInstanceRepository;

/**
 * Executes a {@code userTask}. A user task is a wait state: its pending step is the open user task (instance, node and
 * creation date are those of the {@code ProcessStep}), so it needs no store of its own, and it keeps the instance
 * running.
 * <p>
 * Its execution by the {@code bpmnNodeTask} leaves the step pending and takes no flow (also when the task is delivered
 * again). {@link ProcessEngineService#completeUserTask} completes it from outside
 * ({@link BpmnNodeExecutor#completeUserTask}): the given variables are added to the process variables or replace them
 * (none is removed) and are stored if they change any, then the step is completed and the outgoing flows are taken
 * like for every activity ({@link FlowSelector#activity}), evaluating their conditions against the changed variables.
 * <p>
 * Loop characteristics fail: a standard loop here, a multi-instance loop in the wrapping
 * {@code loop.MultiInstanceLoop}, which needs each instance to complete in one step.
 */
@Component
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

    @Override
    public List<SequenceFlow> invoke(final UserTask node, final NodeExecution execution)
            throws ProcessEngineException {
        if (node.getLoopCharacteristics() != null) {
            throw new ProcessEngineException("User task '" + node.getId()
                    + "' has loop characteristics, which are not supported yet.");
        }
        final ProcessInstance instance = execution.instance();
        final Optional<Map<String, Object>> completion = execution.completion();
        if (completion.isEmpty()) {
            LoggerFactory.getLogger(this.getClass()).debug("User task {} of process instance {} waits in step {}",
                    node.getId(), instance.id(), execution.step().id());
            return List.of();
        }
        final Map<String, Object> variables = new LinkedHashMap<>(instance.variables());
        variables.putAll(completion.get());
        try {
            if (!variables.equals(instance.variables())) {
                this.instances.updateVariables(instance.id(), variables);
            }
        } catch (final NoSuchProcessInstanceException e) {
            throw new ProcessEngineException("Cannot complete user task '" + node.getId() + "' of process instance "
                    + instance.id() + ".", e);
        }
        final List<SequenceFlow> taken = this.flows.activity(node, execution.outgoing(), execution.language(),
                variables);
        execution.complete();
        LoggerFactory.getLogger(this.getClass()).info("Completed user task {} (step {}) of process instance {}",
                node.getId(), execution.step().id(), instance.id());
        return taken;
    }
}
