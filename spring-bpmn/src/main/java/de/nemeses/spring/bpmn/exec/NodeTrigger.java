package de.nemeses.spring.bpmn.exec;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import de.nemeses.spring.bpmn.model.bpmn20.FlowNode;
import de.nemeses.spring.bpmn.model.bpmn20.Process;
import de.nemeses.spring.bpmn.model.bpmn20.SequenceFlow;
import de.nemeses.spring.bpmn.repository.NoSuchProcessInstanceException;
import de.nemeses.spring.bpmn.repository.ProcessInstance;
import de.nemeses.spring.bpmn.repository.ProcessStep;

/**
 * How a node is reached, passed to {@link Invoker#trigger}: the process instance, the incoming flow, the scope the
 * node runs in and, for one instance of a multi-instance activity, its loop data. It creates the step of the node
 * ({@link #createStep()}), schedules a {@value BpmnNodeExecutor#BEAN_NAME} task for a step ({@link #schedule}) and
 * triggers other nodes ({@link #trigger(String, String, UUID)}), all in the caller's transaction.
 */
public final class NodeTrigger {

    private final InvokerRegistry registry;
    private final UUID instanceId;
    private final Process process;
    private final String expressionLanguage;
    private final FlowNode flowNode;
    private final String incomingFlow;
    private final UUID parentStepId;
    private final Integer counter;
    private final Object item;

    @SuppressWarnings("PMD.ExcessiveParameterList") // created by the registry only
    NodeTrigger(final InvokerRegistry registry, final UUID instanceId, final Process process, final String language,
            final FlowNode node, final String incomingFlowId, final UUID parentStepId) {
        this.registry = registry;
        this.instanceId = instanceId;
        this.process = process;
        this.expressionLanguage = language;
        this.flowNode = node;
        this.incomingFlow = incomingFlowId;
        this.parentStepId = parentStepId;
        this.counter = null;
        this.item = null;
    }

    /**
     * Copies a trigger for one instance of a loop ({@link #forLoopInstance}).
     */
    private NodeTrigger(final NodeTrigger original, final UUID activityStepId, final Integer loopCounter,
            final Object loopItem) {
        this.registry = original.registry;
        this.instanceId = original.instanceId;
        this.process = original.process;
        this.expressionLanguage = original.expressionLanguage;
        this.flowNode = original.flowNode;
        this.incomingFlow = original.incomingFlow;
        this.parentStepId = activityStepId;
        this.counter = loopCounter;
        this.item = loopItem;
    }

    /**
     * Creates the trigger of one instance of a multi-instance activity: the same node, reached on the same flow, in
     * the scope of the activity step and with the loop data of the instance.
     *
     * @param activityStepId the id of the pending step of the multi-instance activity
     * @param loopCounter the 0-based index of the instance
     * @param loopItem the element of the input collection of the instance, may be {@code null}
     * @return the trigger of the instance
     */
    public NodeTrigger forLoopInstance(final UUID activityStepId, final int loopCounter, final Object loopItem) {
        return new NodeTrigger(this, activityStepId, loopCounter, loopItem);
    }

    /**
     * @return the flow node reached
     */
    FlowNode node() {
        return this.flowNode;
    }

    /**
     * @return the process instance as stored now, i.e. with the variables as changed by the triggering step
     * @throws ProcessEngineException if the instance does not exist
     */
    public ProcessInstance instance() throws ProcessEngineException {
        try {
            return this.registry.instances().retrieve(this.instanceId);
        } catch (final NoSuchProcessInstanceException e) {
            throw new ProcessEngineException("Cannot trigger node '" + this.flowNode.getId() + "'.", e);
        }
    }

    /**
     * @return the default expression language of the BPMN document
     */
    public String language() {
        return this.expressionLanguage;
    }

    /**
     * @return the id of the sequence flow taken to the node, {@code null} for a start event
     */
    public String incomingFlowId() {
        return this.incomingFlow;
    }

    /**
     * @return the scope the node runs in: the step of the embedded sub-process or multi-instance activity,
     *         {@code null} at the top level
     */
    public UUID scope() {
        return this.parentStepId;
    }

    /**
     * @return the sequence flows arriving at the node
     */
    public Collection<SequenceFlow> incoming() {
        return ProcessNavigator.incoming(this.process, this.flowNode);
    }

    /**
     * Reads the pending steps at the node in the scope of the trigger from the store.
     *
     * @return the pending steps at the node in this scope, oldest first, including a step created by this trigger
     */
    public List<ProcessStep> pendingInScope() {
        return this.registry.steps().pending(this.instanceId, this.flowNode.getId()).stream()
                .filter(pending -> Objects.equals(pending.parentStepId(), this.parentStepId))
                .toList();
    }

    /**
     * Creates a pending step for the node, with the incoming flow, scope and loop data of this trigger.
     *
     * @return the created step
     * @throws ProcessEngineException if the instance does not exist
     */
    public ProcessStep createStep() throws ProcessEngineException {
        try {
            return this.registry.steps().create(this.instanceId, this.flowNode.getId(), this.incomingFlow,
                    this.parentStepId, this.counter, this.item);
        } catch (final NoSuchProcessInstanceException e) {
            throw new ProcessEngineException("Cannot trigger node '" + this.flowNode.getId() + "'.", e);
        }
    }

    /**
     * Schedules the execution of a step: triggers a {@value BpmnNodeExecutor#BEAN_NAME} task with its id.
     *
     * @param step the pending step
     * @return the step
     */
    public ProcessStep schedule(final ProcessStep step) {
        this.registry.taskService().trigger(BpmnNodeExecutor.BEAN_NAME, step.id());
        return step;
    }

    /**
     * Triggers another node of the same process instance through its {@link Invoker}.
     *
     * @param nodeId the id of the flow node to trigger
     * @param incomingFlowId the id of the sequence flow taken to it, {@code null} for a start event
     * @param scope the step of the embedded sub-process it runs in, {@code null} at the top level
     * @return the step created for that node
     * @throws ProcessEngineException if the node does not exist, is not supported or cannot be triggered
     */
    public ProcessStep trigger(final String nodeId, final String incomingFlowId, final UUID scope)
            throws ProcessEngineException {
        return this.registry.trigger(this.instanceId, this.process, this.expressionLanguage,
                ProcessNavigator.flowNode(this.process, nodeId), incomingFlowId, scope);
    }
}
