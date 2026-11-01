package de.nemeses.spring.bpmn.exec;

import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import de.nemeses.spring.bpmn.model.bpmn20.FlowNode;
import de.nemeses.spring.bpmn.model.bpmn20.Process;
import de.nemeses.spring.bpmn.model.bpmn20.SequenceFlow;
import de.nemeses.spring.bpmn.repository.NoSuchProcessStepException;
import de.nemeses.spring.bpmn.repository.ProcessDefinition;
import de.nemeses.spring.bpmn.repository.ProcessInstance;
import de.nemeses.spring.bpmn.repository.ProcessStep;
import de.nemeses.spring.bpmn.repository.ProcessStepRepository;

/**
 * One execution of a step by the {@link BpmnNodeExecutor}, passed to the {@link Invoker} of its node: the (locked)
 * process instance, the step, the flows around the node and the default expression language of the document.
 * <p>
 * An {@link InvokerDecorator} that runs its delegate for one instance of a loop passes it a view of this execution
 * ({@link #forLoopInstance}).
 */
public final class NodeExecution {

    private final ProcessStepRepository steps;
    private final ProcessInstance processInstance;
    private final ProcessStep processStep;
    private final Process process;
    private final FlowNode node;
    private final String expressionLanguage;
    private final List<ProcessStep> pending;
    private final LoopInstance loop;
    private final boolean hasOutgoing;
    private UUID flowScope;
    private boolean completed;

    @SuppressWarnings("PMD.ExcessiveParameterList") // created by the executor only
    NodeExecution(final ProcessStepRepository steps, final ProcessInstance instance, final ProcessStep step,
            final Process process, final FlowNode node, final String language,
            final List<ProcessStep> pendingInScope) {
        this.steps = steps;
        this.processInstance = instance;
        this.processStep = step;
        this.process = process;
        this.node = node;
        this.expressionLanguage = language;
        this.pending = List.copyOf(pendingInScope);
        this.loop = null;
        this.hasOutgoing = true;
        this.flowScope = step.parentStepId();
    }

    /**
     * Copies an execution for one instance of a loop ({@link #forLoopInstance}).
     */
    private NodeExecution(final NodeExecution original, final ProcessInstance instance, final LoopInstance loop) {
        this.steps = original.steps;
        this.processInstance = instance;
        this.processStep = original.processStep;
        this.process = original.process;
        this.node = original.node;
        this.expressionLanguage = original.expressionLanguage;
        this.pending = original.pending;
        this.loop = loop;
        this.hasOutgoing = false;
        this.flowScope = original.processStep.parentStepId();
    }

    /**
     * Creates the view of this execution for one instance of a loop: the instance sees the given variables, has the
     * given {@link #loopInstance()} and no {@link #outgoing()} flows (the decorator takes the flows of the activity
     * once, when the loop is finished).
     *
     * @param variables the process variables the loop instance sees, including its local data items
     * @param loopCounter the 0-based index of the loop instance
     * @param loopItem the element of the input collection of the loop instance, may be {@code null}
     * @return the view
     */
    public NodeExecution forLoopInstance(final Map<String, Object> variables, final int loopCounter,
            final Object loopItem) {
        return new NodeExecution(this, new VariablesView(this.processInstance,
                Collections.unmodifiableMap(new LinkedHashMap<>(variables))), new LoopInstance(loopCounter, loopItem));
    }

    /**
     * @return the (locked) process instance, with its variables before the node
     */
    public ProcessInstance instance() {
        return this.processInstance;
    }

    /**
     * @return the (pending) step being executed
     */
    public ProcessStep step() {
        return this.processStep;
    }

    /**
     * @return the default expression language of the BPMN document (for conditions)
     */
    public String language() {
        return this.expressionLanguage;
    }

    /**
     * @return the sequence flows leaving the node
     */
    public List<SequenceFlow> outgoing() {
        return this.hasOutgoing ? ProcessNavigator.outgoing(this.process, this.node) : List.of();
    }

    /**
     * @return the sequence flows arriving at the node
     */
    public List<SequenceFlow> incoming() {
        return ProcessNavigator.incoming(this.process, this.node);
    }

    /**
     * @return the pending steps at the node in the scope of the step (the same sub-process step, or the top level),
     *         oldest first, including the step itself
     */
    public List<ProcessStep> pendingInScope() {
        return this.pending;
    }

    /**
     * @return the loop instance this execution runs for ({@link #forLoopInstance}), {@code null} for an execution of
     *         the node itself
     */
    public LoopInstance loopInstance() {
        return this.loop;
    }

    /**
     * @return whether the step being executed was completed through this execution ({@link #complete()} or
     *         {@link #complete(List)})
     */
    public boolean isCompleted() {
        return this.completed;
    }

    /**
     * @return the scope the steps of the taken flows are created in: the step of the embedded sub-process they run in,
     *         {@code null} at the top level; by default the scope of the step being executed
     */
    public UUID scope() {
        return this.flowScope;
    }

    /**
     * Changes the scope the steps of the taken flows are created in and that is left when it has no pending step any
     * more, e.g. to the scope of the activity step for an instance step of a multi-instance activity.
     *
     * @param parentStepId the step of the embedded sub-process, {@code null} for the top level
     */
    public void scope(final UUID parentStepId) {
        this.flowScope = parentStepId;
    }

    /**
     * Completes the step being executed.
     *
     * @throws ProcessEngineException if the step does not exist
     */
    public void complete() throws ProcessEngineException {
        this.complete(List.of(this.processStep.id()));
    }

    /**
     * Completes steps of the instance, e.g. all steps joined by a parallel gateway.
     *
     * @param stepIds the ids of the steps to complete
     * @throws ProcessEngineException if a step does not exist
     */
    public void complete(final List<UUID> stepIds) throws ProcessEngineException {
        try {
            this.steps.complete(stepIds, new Date());
            this.completed |= stepIds.contains(this.processStep.id());
        } catch (final NoSuchProcessStepException e) {
            throw new ProcessEngineException("Cannot complete steps " + stepIds + " of process instance "
                    + this.processInstance.id() + ".", e);
        }
    }

    /**
     * One instance of a loop, executed in a step of its own.
     *
     * @param counter the 0-based index of the instance
     * @param item the element of the input collection the instance runs for, {@code null} without input collection
     */
    public record LoopInstance(int counter, Object item) {
    }

    /**
     * A process instance with other variables.
     *
     * @param instance the process instance
     * @param variables the variables to show instead of its own
     */
    private record VariablesView(ProcessInstance instance, Map<String, Object> variables) implements ProcessInstance {

        @Override
        @SuppressWarnings("PMD.ShortMethodName")
        public UUID id() {
            return this.instance.id();
        }

        @Override
        public ProcessDefinition processDefinition() {
            return this.instance.processDefinition();
        }

        @Override
        public Date startDate() {
            return this.instance.startDate();
        }

        @Override
        public Date endDate() {
            return this.instance.endDate();
        }

        @Override
        public ProcessStep parentStep() {
            return this.instance.parentStep();
        }

        @Override
        public ProcessInstance parentInstance() {
            return this.instance.parentInstance();
        }
    }
}
