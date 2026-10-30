package de.nemeses.spring.bpmn;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.xml.namespace.QName;

import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import de.nemeses.spring.bpmn.model.bpmn20.CallActivity;
import de.nemeses.spring.bpmn.model.bpmn20.Definitions;
import de.nemeses.spring.bpmn.model.bpmn20.EndEvent;
import de.nemeses.spring.bpmn.model.bpmn20.ExclusiveGateway;
import de.nemeses.spring.bpmn.model.bpmn20.FlowNode;
import de.nemeses.spring.bpmn.model.bpmn20.ParallelGateway;
import de.nemeses.spring.bpmn.model.bpmn20.Process;
import de.nemeses.spring.bpmn.model.bpmn20.ScriptTask;
import de.nemeses.spring.bpmn.model.bpmn20.SequenceFlow;
import de.nemeses.spring.bpmn.model.bpmn20.ServiceTask;
import de.nemeses.spring.bpmn.model.bpmn20.StartEvent;
import de.nemeses.spring.bpmn.model.bpmn20.Task;
import de.nemeses.spring.bpmn.repository.NoSuchProcessInstanceException;
import de.nemeses.spring.bpmn.repository.NoSuchProcessStepException;
import de.nemeses.spring.bpmn.repository.ProcessInstance;
import de.nemeses.spring.bpmn.repository.ProcessInstanceRepository;
import de.nemeses.spring.bpmn.repository.ProcessStep;
import de.nemeses.spring.bpmn.repository.ProcessStepRepository;
import de.nemeses.spring.tasks.TaskService;

/**
 * Executes single steps (nodes) of process instances; called by the {@link BpmnNodeTask}.
 */
@Component
@SuppressWarnings("PMD.CouplingBetweenObjects") // dispatches over all supported node types
public class BpmnNodeExecutor {

    /** Namespace of the BPMN extension attributes understood by the engine. */
    public static final String NAMESPACE = "https://nemeses.de/spring-bpmn";

    /** Extension attribute of a {@code serviceTask} naming its {@link BpmnNodeImplementation} bean. */
    public static final QName BEAN_ATTRIBUTE = new QName(BpmnNodeExecutor.NAMESPACE, "bean");

    private final ProcessInstanceRepository instances;
    private final ProcessStepRepository steps;
    private final BpmnParser parser;
    private final TaskInvoker tasks;
    private final FlowSelector flows;
    private final SubProcessScopes subProcesses;
    private final CallActivityInvoker callActivities;

    @SuppressWarnings("PMD.ExcessiveParameterList") // constructor injection of the collaborators
    BpmnNodeExecutor(final ProcessInstanceRepository instances, final ProcessStepRepository steps,
            final BpmnParser parser, final TaskInvoker tasks, final FlowSelector flows,
            final SubProcessScopes subProcesses, final CallActivityInvoker callActivities) {
        this.instances = instances;
        this.steps = steps;
        this.parser = parser;
        this.tasks = tasks;
        this.flows = flows;
        this.subProcesses = subProcesses;
        this.callActivities = callActivities;
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
    static ProcessStep schedule(final ProcessStepRepository steps, final TaskService taskService,
            final UUID instanceId, final String nodeId, final String incomingFlowId, final UUID parentStepId)
            throws NoSuchProcessInstanceException {
        final ProcessStep step = steps.create(instanceId, nodeId, incomingFlowId, parentStepId);
        taskService.trigger(BpmnNodeTask.BEAN_NAME, step.id());
        return step;
    }

    /**
     * Executes one step (a node of a process instance), completes it and creates a step and triggers a
     * {@link BpmnNodeTask} for the target of each sequence flow taken, all in one transaction. The process instance is
     * locked first ({@link ProcessInstanceRepository#lock}), so the steps of one instance are executed one after the
     * other. Supported node types:
     * <ul>
     * <li>{@code startEvent}: nothing to do, all outgoing flows are taken.</li>
     * <li>{@code task}: nothing to do, the outgoing flows are taken like for every activity (see below).</li>
     * <li>{@code serviceTask}: calls the {@link BpmnNodeImplementation} bean named by the extension attribute
     * {@link #BEAN_ATTRIBUTE} (through the {@link TaskInvoker}), stores the variables it changed through its
     * {@link BpmnNodeContext} ({@link ProcessInstanceRepository#updateVariables}), then takes the outgoing flows like for every activity,
     * evaluating their conditions against the changed variables.</li>
     * <li>{@code scriptTask}: runs its SpEL script (through the {@link TaskInvoker} and the
     * {@link ScriptTaskInvoker}), then stores the changed variables and takes the outgoing flows like a
     * {@code serviceTask}.</li>
     * <li>{@code exclusiveGateway}: takes the first non-default outgoing flow (in document order) without condition or
     * whose condition is true, else the {@code default} flow; fails if there is none.</li>
     * <li>{@code parallelGateway} with at most one incoming flow: takes all outgoing flows (fork).</li>
     * <li>{@code parallelGateway} with more incoming flows (join): continues only if a step is pending at the gateway
     * for every incoming flow. Then the oldest pending step of each incoming flow is completed and all outgoing flows
     * are taken once. Otherwise the step stays pending (parked at the join) and no flow is taken.</li>
     * <li>{@code callActivity}: a wait state (through the {@link CallActivityInvoker}). The first execution starts an
     * instance of the called process and leaves the step pending; when that child instance ends, the step is executed
     * again, passes the variables back, completes and takes the outgoing flows like for every activity.</li>
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
     * {@code task}, {@code serviceTask} and {@code scriptTask} may have {@code standardLoopCharacteristics}: all iterations run in this
     * step (see {@link ActivityLoop}) and the outgoing flows are taken once after the last iteration;
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
            final List<SequenceFlow> incoming = ProcessNavigator.incoming(process, node);
            final List<SequenceFlow> taken;
            if (node instanceof ParallelGateway && incoming.size() > 1) {
                taken = this.join(instance, process, node, incoming, pendingAtNode.stream()
                        .filter(pending -> SubProcessScopes.inSameScope(pending, step))
                        .toList());
            } else if (this.subProcesses.enter(instance, step, node)) {
                taken = List.of();
            } else if (node instanceof final CallActivity call) {
                taken = this.callActivities.execute(instance, step, call,
                        ProcessNavigator.outgoing(process, node), document.getExpressionLanguage());
            } else {
                taken = this.run(instance, document.getExpressionLanguage(), process, node);
                this.steps.complete(List.of(stepId), new Date());
            }
            this.subProcesses.take(instance, taken, step.parentStepId());
            if (node instanceof EndEvent && step.parentStepId() == null) {
                this.end(instance, node);
            }
            this.subProcesses.leaveCompleted(instance, document.getExpressionLanguage(), process,
                    step.parentStepId());
            LoggerFactory.getLogger(this.getClass()).debug(
                    "Executed step {} (node {}) of process instance {}, taken flows {}",
                    stepId, step.nodeId(), instance.id(), taken.stream().map(SequenceFlow::getId).toList());
        } catch (final NoSuchProcessStepException | NoSuchProcessInstanceException e) {
            throw new ProcessEngineException("Cannot execute process step " + stepId + ".", e);
        }
    }

    /**
     * Synchronizes a joining parallel gateway: completes the oldest pending step of each incoming flow if there is one
     * for every incoming flow.
     *
     * @return the outgoing flows if the gateway continues, else no flow
     */
    private List<SequenceFlow> join(final ProcessInstance instance, final Process process, final FlowNode gateway,
            final List<SequenceFlow> incoming, final List<ProcessStep> pendingAtNode)
            throws ProcessEngineException, NoSuchProcessStepException {
        final List<UUID> joined = new ArrayList<>();
        for (final SequenceFlow flow : incoming) {
            final UUID oldest = pendingAtNode.stream()
                    .filter(pending -> flow.getId().equals(pending.incomingFlowId()))
                    .map(ProcessStep::id)
                    .findFirst()
                    .orElse(null);
            if (oldest == null) {
                LoggerFactory.getLogger(this.getClass()).debug(
                        "Parallel gateway {} of process instance {} waits for flow {}",
                        gateway.getId(), instance.id(), flow.getId());
                return List.of();
            }
            joined.add(oldest);
        }
        final List<SequenceFlow> outgoing = FlowSelector.unconditional(
                ProcessNavigator.outgoing(process, gateway));
        this.steps.complete(joined, new Date());
        return outgoing;
    }

    /**
     * Runs the node according to its type.
     *
     * @param language the default expression language of the document
     * @return the sequence flows to take
     */
    private List<SequenceFlow> run(final ProcessInstance instance, final String language, final Process process,
            final FlowNode node) throws ProcessEngineException {
        final List<SequenceFlow> outgoing = ProcessNavigator.outgoing(process, node);
        return switch (node) {
        case StartEvent _, ParallelGateway _ -> FlowSelector.unconditional(outgoing);
        case final EndEvent event -> {
            BpmnNodeExecutor.checkEndEvent(event);
            yield List.of();
        }
        case final ServiceTask task -> {
            final Map<String, Object> variables = this.tasks.invoke(instance, task, language);
            yield this.flows.activity(task, outgoing, language, variables);
        }
        case final ScriptTask task -> {
            final Map<String, Object> variables = this.tasks.invoke(instance, task, language);
            yield this.flows.activity(task, outgoing, language, variables);
        }
        case final ExclusiveGateway gateway ->
            List.of(this.flows.exclusive(gateway.getId(), gateway.getDefault(), outgoing, language, instance.variables()));
        case final Task task when task.getClass() == Task.class -> {
            final Map<String, Object> variables = this.tasks.loop(instance, task, language);
            yield this.flows.activity(task, outgoing, language, variables);
        }
        default -> throw new ProcessEngineException("Node '" + node.getId() + "' is a "
                + node.getClass().getSimpleName() + ", which is not supported yet.");
        };
    }

    private static void checkEndEvent(final EndEvent event) throws ProcessEngineException {
        if (!event.getEventDefinition().isEmpty() || !event.getEventDefinitionRef().isEmpty()) {
            throw new ProcessEngineException("End event '" + event.getId()
                    + "' has an event definition, which is not supported yet.");
        }
    }

    /**
     * Ends the instance if none of its steps is pending any more.
     */
    private void end(final ProcessInstance instance, final FlowNode event) throws NoSuchProcessInstanceException {
        final long pending = this.steps.countPending(instance.id());
        if (pending > 0) {
            LoggerFactory.getLogger(this.getClass()).debug(
                    "Reached end event {} of process instance {}, which still has {} pending steps",
                    event.getId(), instance.id(), pending);
            return;
        }
        this.instances.updateEndDate(instance.id(), new Date());
        LoggerFactory.getLogger(this.getClass()).info("Process instance {} ended at {}", instance.id(), event.getId());
        this.callActivities.resumeCaller(instance);
    }
}
