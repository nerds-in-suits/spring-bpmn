package de.nemeses.spring.bpmn.exec;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.xml.namespace.QName;

import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import de.nemeses.spring.bpmn.model.bpmn20.Definitions;
import de.nemeses.spring.bpmn.model.bpmn20.FlowNode;
import de.nemeses.spring.bpmn.model.bpmn20.Process;
import de.nemeses.spring.bpmn.model.bpmn20.SequenceFlow;
import de.nemeses.spring.bpmn.model.bpmn20.UserTask;
import de.nemeses.spring.bpmn.repository.NoSuchProcessInstanceException;
import de.nemeses.spring.bpmn.repository.NoSuchProcessStepException;
import de.nemeses.spring.bpmn.repository.ProcessInstance;
import de.nemeses.spring.bpmn.repository.ProcessInstanceRepository;
import de.nemeses.spring.bpmn.repository.ProcessStep;
import de.nemeses.spring.bpmn.repository.ProcessStepRepository;
import de.nemeses.spring.tasks.JsonPayloadTaskImplementation;
import de.nemeses.spring.tasks.TaskException;

/**
 * Executes single steps (nodes) of process instances; the task bean {@value #BEAN_NAME}, whose payload is the id of
 * the {@link ProcessStep} to execute.
 * <p>
 * Each step runs in one transaction. The process instance is locked first ({@link ProcessInstanceRepository#lock}),
 * so the steps of one instance run one after the other, and the pending steps are read after locking. The node is run
 * by {@link Invoker#execute} of the {@link Invoker} for its type ({@link InvokerRegistry}). Running the node,
 * completing its step, storing changed variables and triggering the target nodes of the flows taken
 * ({@link Invoker#trigger}) all happen in that transaction; a {@link ProcessEngineException} rolls all of it back and
 * fails the task as a {@link TaskException}; whether it is retried depends on the task backend. The supported node
 * types are listed at {@link #executeNode} and in {@code docs/bpmn-support.md}.
 * <p>
 * While a step is executed ({@link #executeNode}, {@link #completeUserTask}), the SLF4J {@link MDC} holds the id of its
 * process instance under {@value #MDC_PROCESS_INSTANCE_ID} and its own id under {@value #MDC_STEP_ID}; afterwards both
 * keys are removed, also if the step fails.
 */
@Component(BpmnNodeExecutor.BEAN_NAME)
public class BpmnNodeExecutor extends JsonPayloadTaskImplementation<UUID> {

    /** Bean name under which the task is scheduled with the {@code TaskService}; persisted tasks refer to it. */
    public static final String BEAN_NAME = "bpmnNodeTask";

    /** Namespace of the BPMN extension attributes understood by the engine. */
    public static final String NAMESPACE = "https://nemeses.de/spring-bpmn";

    /** Extension attribute of a {@code serviceTask} naming its {@link BpmnNodeImplementation} bean. */
    public static final QName BEAN_ATTRIBUTE = new QName(BpmnNodeExecutor.NAMESPACE, "bean");

    /** {@link MDC} key of the id of the process instance whose step is executed. */
    public static final String MDC_PROCESS_INSTANCE_ID = "pid";

    /** {@link MDC} key of the id of the process step that is executed. */
    public static final String MDC_STEP_ID = "step";

    private final ProcessInstanceRepository instances;
    private final ProcessStepRepository steps;
    private final ProcessDefinitionModelCache models;
    private final SubProcessScopes subProcesses;
    private final InvokerRegistry invokers;

    BpmnNodeExecutor(final ProcessInstanceRepository instances, final ProcessStepRepository steps,
            final ProcessDefinitionModelCache models, final SubProcessScopes subProcesses,
            final InvokerRegistry invokers) {
        super();
        this.instances = instances;
        this.steps = steps;
        this.models = models;
        this.subProcesses = subProcesses;
        this.invokers = invokers;
    }

    @Override
    protected Class<UUID> payloadType() {
        return UUID.class;
    }

    /**
     * Entry point of the {@code TaskService}: deserializes the step id and executes the step like
     * {@link #execute(UUID)}. Overridden only to be transactional, because the inherited default method calls
     * {@link #execute(UUID)} on this object and so bypasses the transactional proxy.
     *
     * @param serializedPayload the step id as JSON
     * @throws TaskException if the step failed; the transaction is rolled back
     */
    @Override
    @Transactional(rollbackFor = TaskException.class)
    public void executeSerialized(final String serializedPayload) throws TaskException {
        this.execute(this.deserializePayload(serializedPayload));
    }

    /**
     * Executes the step in one transaction through {@link #executeNode}.
     *
     * @param stepId the id of the process step
     * @throws TaskException with the {@link ProcessEngineException} as cause if the step failed; the transaction is
     *             rolled back
     */
    @Override
    @Transactional(rollbackFor = TaskException.class)
    public void execute(final UUID stepId) throws TaskException {
        try {
            this.executeNode(stepId);
        } catch (final ProcessEngineException e) {
            throw new TaskException("Cannot execute process step " + stepId + ".", e);
        }
    }

    /**
     * Executes one step (a node of a process instance) with {@link Invoker#execute} of the {@link Invoker} for the
     * node's type (as enhanced by the {@link InvokerEnhancer} beans) and triggers the target of each sequence flow
     * taken with {@link Invoker#trigger}, all in one transaction. The process instance is locked first
     * ({@link ProcessInstanceRepository#lock}), so the steps of one instance are executed one after the other.
     * Supported node types (one {@link Invoker} each, in the sub-packages):
     * <ul>
     * <li>{@code startEvent}: nothing to do, all outgoing flows are taken.</li>
     * <li>{@code task}: nothing to do, the outgoing flows are taken like for every activity (see below).</li>
     * <li>{@code serviceTask}: calls the {@link BpmnNodeImplementation} bean named by the extension attribute
     * {@link #BEAN_ATTRIBUTE}, stores the variables it changed through its {@link BpmnNodeContext}
     * ({@link ProcessInstanceRepository#updateVariables}), then takes the outgoing flows like for every activity,
     * evaluating their conditions against the changed variables.</li>
     * <li>{@code scriptTask}: runs its SpEL script, then stores the changed variables and takes the outgoing flows
     * like a {@code serviceTask}.</li>
     * <li>{@code exclusiveGateway}: takes the first non-default outgoing flow (in document order) without condition or
     * whose condition is true, else the {@code default} flow; fails if there is none.</li>
     * <li>{@code parallelGateway} with at most one incoming flow: takes all outgoing flows (fork).</li>
     * <li>{@code parallelGateway} with more incoming flows (join): reaching it creates a step, which gets a task only
     * if a step is then pending at the gateway for every incoming flow. Executing it completes the oldest pending step
     * of each incoming flow and takes all outgoing flows once; if the set is no longer complete, nothing happens.</li>
     * <li>{@code callActivity}: a wait state. Reaching it starts an instance of the called process; when that child
     * instance ends, the step is executed, passes the variables back, completes and takes the outgoing flows like for
     * every activity.</li>
     * <li>{@code userTask}: a wait state. Reaching it only creates its step, which gets no task; {@link #completeUserTask}
     * executes it from outside, merges the given variables and takes the outgoing flows like for every activity.</li>
     * <li>{@code endEvent} without event definition: ends the instance (sets its end date) if no other step of the
     * instance is pending; if the instance was started by a call activity, triggers a {@value #BEAN_NAME} task for the
     * waiting call activity step of the parent instance. Inside a sub-process it only completes its step (see
     * below).</li>
     * <li>embedded {@code subProcess}: reaching it creates its step and triggers each start event inside it, with the
     * sub-process step as {@link ProcessStep#parentStepId() parent}; the sub-process step stays pending. Steps inside
     * it are created with the same parent. When no child step of the sub-process step is pending any more, a task is
     * triggered for the sub-process step, whose execution completes it and takes the outgoing flows of the
     * sub-process like for every activity (with the variables as changed inside it).</li>
     * </ul>
     * An activity takes every outgoing flow without condition and every flow whose condition is true; its
     * {@code default} flow only if no condition is true. Conditions are evaluated by the {@link ConditionEvaluator}
     * (through the {@link FlowSelector}). Flows out of start events and parallel gateways must not have a condition.
     * {@code task}, {@code serviceTask} and {@code scriptTask} may have {@code standardLoopCharacteristics}: all
     * iterations run in this step and the outgoing flows are taken once after the last iteration. They may have
     * {@code multiInstanceLoopCharacteristics} instead: each instance runs in a step of its own, whose parent is the
     * pending step of the activity, and the outgoing flows are taken once, in the scope of the activity step, when the
     * loop is finished (see {@code loop.MultiInstanceLoop}, added by an {@link InvokerEnhancer}).
     * A step that is no longer pending (a task delivered again, or a join step completed by another step) and the steps
     * of an instance that has already ended are skipped. A target node that is not supported or cannot be reached
     * fails this step.
     *
     * @param stepId the id of the process step
     * @throws ProcessEngineException if the step, instance or node does not exist, the node type or a sequence flow
     *             condition is not supported, the node's implementation failed or a target node cannot be triggered
     */
    @Transactional(rollbackFor = ProcessEngineException.class)
    public void executeNode(final UUID stepId) throws ProcessEngineException {
        this.executeStep(stepId, null);
    }

    /**
     * Completes a waiting {@code userTask} step from outside: like {@link #executeNode}, in one transaction under the
     * lock of the instance, but the execution carries the given variables ({@link NodeExecution#completion()}), which
     * the invoker of {@code userTask} merges into the process variables before it completes the step and takes the
     * outgoing flows like for every activity. Unlike {@link #executeNode}, a step that is no longer pending or whose
     * instance has ended is not skipped but rejected.
     *
     * @param stepId the id of the pending {@code userTask} step
     * @param variables the variables to set (added or replaced, none removed); {@code null} means none; keys and
     *            values must not be {@code null}
     * @throws ProcessEngineException if the step does not exist, is not pending, is not at a {@code userTask}, its
     *             instance has ended, a key or value is {@code null}, or the flows cannot be taken
     */
    @Transactional(rollbackFor = ProcessEngineException.class)
    public void completeUserTask(final UUID stepId, final Map<String, Object> variables)
            throws ProcessEngineException {
        final Map<String, Object> completion = variables == null ? Map.of() : variables;
        for (final Map.Entry<String, Object> variable : completion.entrySet()) {
            if (variable.getKey() == null || variable.getValue() == null) {
                throw new ProcessEngineException("Cannot complete user task step " + stepId
                        + " with the null variable or value " + variable.getKey() + ".");
            }
        }
        this.executeStep(stepId, completion);
    }

    /**
     * Executes a step; with {@code completion}, completes a waiting {@code userTask} step from outside. Meanwhile the
     * step id and the process instance id are in the {@link MDC}; both keys are removed afterwards. The step is
     * retrieved before the keys are put, since the process instance id comes from it; a missing step or process
     * instance fails as a {@link ProcessEngineException}.
     */
    private void executeStep(final UUID stepId, final Map<String, Object> completion) throws ProcessEngineException {
        try {
            final ProcessStep step = this.steps.retrieve(stepId);
            try (MDC.MDCCloseable _ = MDC.putCloseable(BpmnNodeExecutor.MDC_STEP_ID, stepId.toString());
                    MDC.MDCCloseable _ = MDC.putCloseable(BpmnNodeExecutor.MDC_PROCESS_INSTANCE_ID,
                            step.processInstanceId().toString())) {
                final ProcessInstance instance = this.instances.lock(step.processInstanceId());
                // read after locking, so steps completed by a concurrent execution are seen
                final List<ProcessStep> pendingAtNode = this.steps.pending(instance.id(), step.nodeId());
                if (pendingAtNode.stream().noneMatch(pending -> pending.id().equals(stepId))) {
                    if (completion != null) {
                        throw new ProcessEngineException("Cannot complete user task step " + stepId
                                + ", which is already completed.");
                    }
                    LoggerFactory.getLogger(this.getClass()).info(
                            "Skipping step {} (node {}) of process instance {}, which is already completed.",
                            stepId, step.nodeId(), instance.id());
                    return;
                }
                if (instance.endDate() != null) {
                    if (completion != null) {
                        throw new ProcessEngineException("Cannot complete user task step " + stepId
                                + " of process instance " + instance.id() + ", which has ended.");
                    }
                    LoggerFactory.getLogger(this.getClass()).warn(
                            "Skipping step {} (node {}) of process instance {}, which has ended.",
                            stepId, step.nodeId(), instance.id());
                    return;
                }
                final Definitions document = this.models.document(instance.processDefinition());
                final Process process = ProcessEngineService.process(document, instance.processDefinition());
                final FlowNode node = ProcessNavigator.flowNode(process, step.nodeId());
                if (completion != null && node.getClass() != UserTask.class) {
                    throw new ProcessEngineException("Cannot complete step " + stepId + ", node '" + node.getId()
                            + "' is a " + node.getClass().getSimpleName() + ", not a UserTask.");
                }
                final Invoker<?> invoker = this.invokers.invoker(node);
                final NodeExecution execution = new NodeExecution(this.invokers, instance, step, process, node,
                        document.getExpressionLanguage(), pendingAtNode.stream()
                                .filter(pending -> SubProcessScopes.inSameScope(pending, step))
                                .toList());
                if (completion != null) {
                    execution.completion(completion);
                }
                final Collection<SequenceFlow> taken = BpmnNodeExecutor.run(invoker, node, execution);
                this.subProcesses.take(instance.id(), process, document.getExpressionLanguage(), taken,
                        execution.scope());
                this.subProcesses.leaveCompleted(instance.id(), execution.scope());
                LoggerFactory.getLogger(this.getClass()).debug(
                        "Executed step {} (node {}) of process instance {}, taken flows {}",
                        stepId, step.nodeId(), instance.id(), taken.stream().map(SequenceFlow::getId).toList());
            }
        } catch (final NoSuchProcessStepException | NoSuchProcessInstanceException e) {
            throw new ProcessEngineException("Cannot execute process step " + stepId + ".", e);
        }
    }

    private static <T extends FlowNode> Collection<SequenceFlow> run(final Invoker<T> invoker,
            final FlowNode node, final NodeExecution execution) throws ProcessEngineException {
        return invoker.execute(invoker.nodeType().cast(node), execution);
    }
}
