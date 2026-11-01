package de.nemeses.spring.bpmn.exec;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import de.nemeses.spring.bpmn.model.bpmn20.FlowNode;
import de.nemeses.spring.bpmn.model.bpmn20.Process;
import de.nemeses.spring.bpmn.model.bpmn20.SequenceFlow;
import de.nemeses.spring.bpmn.repository.NoSuchProcessStepException;
import de.nemeses.spring.bpmn.repository.ProcessInstance;
import de.nemeses.spring.bpmn.repository.ProcessStep;
import de.nemeses.spring.bpmn.repository.ProcessStepRepository;

/**
 * One execution of a step by the {@link BpmnNodeExecutor}, passed to the {@link Invoker} of its node: the (locked)
 * process instance, the step, the flows around the node and the default expression language of the document.
 */
public final class NodeExecution {

    private final ProcessStepRepository steps;
    private final ProcessInstance processInstance;
    private final ProcessStep processStep;
    private final Process process;
    private final FlowNode node;
    private final String expressionLanguage;
    private final List<ProcessStep> pending;

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
        return ProcessNavigator.outgoing(this.process, this.node);
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
        } catch (final NoSuchProcessStepException e) {
            throw new ProcessEngineException("Cannot complete steps " + stepIds + " of process instance "
                    + this.processInstance.id() + ".", e);
        }
    }
}
