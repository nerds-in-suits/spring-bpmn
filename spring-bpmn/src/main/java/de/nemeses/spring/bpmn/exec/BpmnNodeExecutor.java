package de.nemeses.spring.bpmn.exec;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.xml.namespace.QName;

import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import de.nemeses.spring.bpmn.BpmnParser;
import de.nemeses.spring.bpmn.model.bpmn20.Definitions;
import de.nemeses.spring.bpmn.model.bpmn20.FlowNode;
import de.nemeses.spring.bpmn.model.bpmn20.Process;
import de.nemeses.spring.bpmn.model.bpmn20.SequenceFlow;
import de.nemeses.spring.bpmn.repository.NoSuchProcessInstanceException;
import de.nemeses.spring.bpmn.repository.NoSuchProcessStepException;
import de.nemeses.spring.bpmn.repository.ProcessInstance;
import de.nemeses.spring.bpmn.repository.ProcessInstanceRepository;
import de.nemeses.spring.bpmn.repository.ProcessStep;
import de.nemeses.spring.bpmn.repository.ProcessStepRepository;
import de.nemeses.spring.tasks.TaskService;

/**
 * Executes single steps (nodes) of process instances; called by the {@link BpmnNodeTask}.
 * <p>
 * Each step runs in one transaction. The process instance is locked first ({@link ProcessInstanceRepository#lock}),
 * so the steps of one instance run one after the other, and the pending steps are read after locking. The node is run
 * by the {@link Invoker} for its type (all {@code Invoker} beans are injected and looked up by the exact class of the
 * node, {@link Invoker#nodeType()}). Running the node, completing its step, storing changed variables and creating the
 * steps and {@link BpmnNodeTask}s of the flows taken all happen in that transaction; a {@link ProcessEngineException}
 * rolls all of it back. The supported node types are listed at {@link #executeNode} and in
 * {@code docs/bpmn-support.md}.
 */
@Component
public class BpmnNodeExecutor {

    /** Namespace of the BPMN extension attributes understood by the engine. */
    public static final String NAMESPACE = "https://nemeses.de/spring-bpmn";

    /** Extension attribute of a {@code serviceTask} naming its {@link BpmnNodeImplementation} bean. */
    public static final QName BEAN_ATTRIBUTE = new QName(BpmnNodeExecutor.NAMESPACE, "bean");

    private final ProcessInstanceRepository instances;
    private final ProcessStepRepository steps;
    private final BpmnParser parser;
    private final SubProcessScopes subProcesses;
    private final Map<Class<? extends FlowNode>, Invoker<?>> invokers;

    BpmnNodeExecutor(final ProcessInstanceRepository instances, final ProcessStepRepository steps,
            final BpmnParser parser, final SubProcessScopes subProcesses, final List<Invoker<?>> invokers) {
        this.instances = instances;
        this.steps = steps;
        this.parser = parser;
        this.subProcesses = subProcesses;
        this.invokers = BpmnNodeExecutor.byNodeType(invokers);
    }

    /**
     * @return the invokers by their node type
     * @throws IllegalStateException if two invokers execute the same node type
     */
    private static Map<Class<? extends FlowNode>, Invoker<?>> byNodeType(final List<Invoker<?>> invokers) {
        final Map<Class<? extends FlowNode>, Invoker<?>> byType = new HashMap<>();
        for (final Invoker<?> invoker : invokers) {
            final Invoker<?> other = byType.putIfAbsent(invoker.nodeType(), invoker);
            if (other != null) {
                throw new IllegalStateException("Invokers " + other.getClass().getName() + " and "
                        + invoker.getClass().getName() + " both execute " + invoker.nodeType().getSimpleName()
                        + ".");
            }
        }
        return Map.copyOf(byType);
    }

    /**
     * Creates a pending step for a node and triggers a {@link BpmnNodeTask} with its id.
     *
     * @param steps the step repository
     * @param taskService the task service
     * @param instanceId the id of the process instance
     * @param nodeId the id of the flow node to execute
     * @param incomingFlowId the id of the sequence flow taken to the node, {@code null} for a start event
     * @param parentStepId the id of the step of the embedded sub-process the node runs in, {@code null} at the top
     *            level
     * @return the created step
     * @throws NoSuchProcessInstanceException if the instance does not exist
     */
    public static ProcessStep schedule(final ProcessStepRepository steps, final TaskService taskService,
            final UUID instanceId, final String nodeId, final String incomingFlowId, final UUID parentStepId)
            throws NoSuchProcessInstanceException {
        final ProcessStep step = steps.create(instanceId, nodeId, incomingFlowId, parentStepId);
        taskService.trigger(BpmnNodeTask.BEAN_NAME, step.id());
        return step;
    }

    /**
     * Executes one step (a node of a process instance) with the {@link Invoker} for the node's type and creates a step
     * and triggers a {@link BpmnNodeTask} for the target of each sequence flow taken, all in one transaction. The
     * process instance is locked first ({@link ProcessInstanceRepository#lock}), so the steps of one instance are
     * executed one after the other. Supported node types (one {@link Invoker} each, in the sub-packages):
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
     * <li>{@code parallelGateway} with more incoming flows (join): continues only if a step is pending at the gateway
     * for every incoming flow. Then the oldest pending step of each incoming flow is completed and all outgoing flows
     * are taken once. Otherwise the step stays pending (parked at the join) and no flow is taken.</li>
     * <li>{@code callActivity}: a wait state. The first execution starts an instance of the called process and leaves
     * the step pending; when that child instance ends, the step is executed again, passes the variables back,
     * completes and takes the outgoing flows like for every activity.</li>
     * <li>{@code endEvent} without event definition: ends the instance (sets its end date) if no other step of the
     * instance is pending; if the instance was started by a call activity, triggers a {@link BpmnNodeTask} for the
     * waiting call activity step of the parent instance. Inside a sub-process it only completes its step (see
     * below).</li>
     * <li>embedded {@code subProcess}: creates a step for each start event inside it, with the sub-process step as
     * {@link ProcessStep#parentStepId() parent}; the sub-process step stays pending. Steps inside it are created with
     * the same parent. When no child step of the sub-process step is pending any more, the sub-process step is
     * completed and the outgoing flows of the sub-process are taken like for every activity (with the variables as
     * changed inside it); if it takes none, the enclosing sub-process is checked the same way.</li>
     * </ul>
     * An activity takes every outgoing flow without condition and every flow whose condition is true; its
     * {@code default} flow only if no condition is true. Conditions are evaluated by the {@link ConditionEvaluator}
     * (through the {@link FlowSelector}). Flows out of start events and parallel gateways must not have a condition.
     * {@code task}, {@code serviceTask} and {@code scriptTask} may have {@code standardLoopCharacteristics}: all
     * iterations run in this step and the outgoing flows are taken once after the last iteration;
     * {@code multiInstanceLoopCharacteristics} are not supported yet.
     * A step that is no longer pending (a task delivered again, or a join step completed by another step) and the steps
     * of an instance that has already ended are skipped.
     *
     * @param stepId the id of the process step
     * @throws ProcessEngineException if the step, instance or node does not exist, the node type or a sequence flow
     *             condition is not supported, or the node's implementation failed
     */
    @Transactional(rollbackFor = ProcessEngineException.class)
    public void executeNode(final UUID stepId) throws ProcessEngineException {
        try {
            final ProcessStep step = this.steps.retrieve(stepId);
            final ProcessInstance instance = this.instances.lock(step.processInstanceId());
            // read after locking, so steps completed by a concurrent execution are seen
            final List<ProcessStep> pendingAtNode = this.steps.pending(instance.id(), step.nodeId());
            if (pendingAtNode.stream().noneMatch(pending -> pending.id().equals(stepId))) {
                LoggerFactory.getLogger(this.getClass()).info(
                        "Skipping step {} (node {}) of process instance {}, which is already completed.",
                        stepId, step.nodeId(), instance.id());
                return;
            }
            if (instance.endDate() != null) {
                LoggerFactory.getLogger(this.getClass()).warn(
                        "Skipping step {} (node {}) of process instance {}, which has ended.",
                        stepId, step.nodeId(), instance.id());
                return;
            }
            final Definitions document = ProcessEngineService.parse(this.parser, instance.processDefinition());
            final Process process = ProcessEngineService.process(document, instance.processDefinition());
            final FlowNode node = ProcessNavigator.flowNode(process, step.nodeId());
            final Invoker<?> invoker = this.invokers.get(node.getClass());
            if (invoker == null) {
                throw new ProcessEngineException("Node '" + node.getId() + "' is a "
                        + node.getClass().getSimpleName() + ", which is not supported yet.");
            }
            final NodeExecution execution = new NodeExecution(this.steps, instance, step, process, node,
                    document.getExpressionLanguage(), pendingAtNode.stream()
                            .filter(pending -> SubProcessScopes.inSameScope(pending, step))
                            .toList());
            final List<SequenceFlow> taken = BpmnNodeExecutor.invoke(invoker, node, execution);
            this.subProcesses.take(instance, taken, step.parentStepId());
            this.subProcesses.leaveCompleted(instance, document.getExpressionLanguage(), process,
                    step.parentStepId());
            LoggerFactory.getLogger(this.getClass()).debug(
                    "Executed step {} (node {}) of process instance {}, taken flows {}",
                    stepId, step.nodeId(), instance.id(), taken.stream().map(SequenceFlow::getId).toList());
        } catch (final NoSuchProcessStepException | NoSuchProcessInstanceException e) {
            throw new ProcessEngineException("Cannot execute process step " + stepId + ".", e);
        }
    }

    private static <T extends FlowNode> List<SequenceFlow> invoke(final Invoker<T> invoker, final FlowNode node,
            final NodeExecution execution) throws ProcessEngineException {
        return invoker.invoke(invoker.nodeType().cast(node), execution);
    }
}
