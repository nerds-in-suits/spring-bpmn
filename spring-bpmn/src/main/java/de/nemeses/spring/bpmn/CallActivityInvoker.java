package de.nemeses.spring.bpmn;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.xml.namespace.QName;

import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;

import de.nemeses.spring.bpmn.model.bpmn20.CallActivity;
import de.nemeses.spring.bpmn.model.bpmn20.SequenceFlow;
import de.nemeses.spring.bpmn.repository.NoSuchProcessDefinitionException;
import de.nemeses.spring.bpmn.repository.NoSuchProcessInstanceException;
import de.nemeses.spring.bpmn.repository.NoSuchProcessStepException;
import de.nemeses.spring.bpmn.repository.ProcessDefinition;
import de.nemeses.spring.bpmn.repository.ProcessDefinitionService;
import de.nemeses.spring.bpmn.repository.ProcessInstance;
import de.nemeses.spring.bpmn.repository.ProcessInstanceRepository;
import de.nemeses.spring.bpmn.repository.ProcessStep;
import de.nemeses.spring.bpmn.repository.ProcessStepRepository;
import de.nemeses.spring.tasks.TaskService;

/**
 * Executes a {@code callActivity} step for the {@link BpmnNodeExecutor}. A call activity is a wait state: its first
 * execution starts a child instance of the called process and leaves the step pending; the end of the child triggers
 * the step again, which then passes the variables back, completes the step and takes the outgoing flows.
 * <p>
 * Variables: without mapping, the child starts with a copy of all parent variables and all final child variables are
 * merged into the parent variables when it ends. As soon as the call activity has any {@link #IN} or {@link #OUT}
 * extension element ({@code <spring:in source="a" target="b"/>}), only the mapped variables are passed, in both
 * directions: {@code in} copies the parent variable {@code source} to the child variable {@code target}, {@code out}
 * copies the child variable {@code source} to the parent variable {@code target}. A mapping whose source variable does
 * not exist is ignored.
 */
@Component
class CallActivityInvoker {

    /** Extension element mapping a parent variable to a variable of the called instance. */
    static final QName IN = new QName(BpmnNodeExecutor.NAMESPACE, "in");

    /** Extension element mapping a variable of the ended called instance back to a parent variable. */
    static final QName OUT = new QName(BpmnNodeExecutor.NAMESPACE, "out");

    private final ProcessDefinitionService definitions;
    private final ProcessInstanceRepository instances;
    private final ProcessStepRepository steps;
    private final ProcessEngineService engine;
    private final FlowSelector flows;
    private final TaskService taskService;

    CallActivityInvoker(final ProcessDefinitionService definitions, final ProcessInstanceRepository instances,
            final ProcessStepRepository steps, final ProcessEngineService engine, final FlowSelector flows,
            final TaskService taskService) {
        this.definitions = definitions;
        this.instances = instances;
        this.steps = steps;
        this.engine = engine;
        this.flows = flows;
        this.taskService = taskService;
    }

    /**
     * Triggers a {@link BpmnNodeTask} for the call activity step waiting for the ended instance, if it was started by
     * one; the step runs in its own task, which locks the parent instance.
     *
     * @param ended the instance that has just ended
     */
    void resumeCaller(final ProcessInstance ended) {
        final ProcessStep parentStep = ended.parentStep();
        if (parentStep != null) {
            this.taskService.trigger(BpmnNodeTask.BEAN_NAME, parentStep.id());
        }
    }

    /**
     * Starts the called process if the step has no child instance yet; continues if the child has ended.
     *
     * @param instance the (locked) parent instance
     * @param step the pending call activity step
     * @param call the call activity
     * @param outgoing the outgoing sequence flows of the call activity
     * @param language the default expression language of the document
     * @return the sequence flows to take; none while the child instance is running
     * @throws ProcessEngineException if {@code calledElement} is missing or unknown, a mapping is invalid, the child
     *             cannot be started or a flow condition fails
     */
    List<SequenceFlow> execute(final ProcessInstance instance, final ProcessStep step, final CallActivity call,
            final List<SequenceFlow> outgoing, final String language) throws ProcessEngineException {
        if (call.getLoopCharacteristics() != null) {
            throw new ProcessEngineException("Activity '" + call.getId()
                    + "' has loop characteristics, which are not supported yet.");
        }
        final List<Mapping> mappings = CallActivityInvoker.mappings(call);
        final Optional<ProcessInstance> child = this.instances.retrieveChild(step.id());
        if (child.isEmpty()) {
            final ProcessInstance started = this.engine.startProcess(this.calledDefinition(call),
                    CallActivityInvoker.input(mappings, instance.variables()), step);
            LoggerFactory.getLogger(this.getClass()).debug(
                    "Call activity {} of process instance {} waits for process instance {}",
                    call.getId(), instance.id(), started.id());
            return List.of();
        }
        if (child.get().endDate() == null) {
            LoggerFactory.getLogger(this.getClass()).info(
                    "Skipping step {} (call activity {}) of process instance {}, process instance {} is still running.",
                    step.id(), call.getId(), instance.id(), child.get().id());
            return List.of();
        }
        final Map<String, Object> variables = CallActivityInvoker.output(mappings, instance.variables(),
                child.get().variables());
        try {
            if (!variables.equals(instance.variables())) {
                this.instances.updateVariables(instance.id(), variables);
            }
            this.steps.complete(List.of(step.id()), new Date());
        } catch (final NoSuchProcessInstanceException | NoSuchProcessStepException e) {
            throw new ProcessEngineException("Cannot complete call activity '" + call.getId()
                    + "' of process instance " + instance.id() + ".", e);
        }
        return this.flows.activity(call, outgoing, language, variables);
    }

    /**
     * Resolves {@code calledElement} by its local part to the latest version of the process definition of that name.
     */
    private ProcessDefinition calledDefinition(final CallActivity call) throws ProcessEngineException {
        final QName calledElement = call.getCalledElement();
        if (calledElement == null || calledElement.getLocalPart().isEmpty()) {
            throw new ProcessEngineException("Call activity '" + call.getId() + "' has no calledElement.");
        }
        try {
            return this.definitions.retrieveLatest(calledElement.getLocalPart());
        } catch (final NoSuchProcessDefinitionException e) {
            throw new ProcessEngineException("Call activity '" + call.getId() + "' calls process '"
                    + calledElement.getLocalPart() + "', which is not defined.", e);
        }
    }

    private static List<Mapping> mappings(final CallActivity call) throws ProcessEngineException {
        final List<Mapping> mappings = new ArrayList<>();
        if (call.getExtensionElements() == null) {
            return mappings;
        }
        for (final Object any : call.getExtensionElements().getAny()) {
            if (any instanceof final Element element && BpmnNodeExecutor.NAMESPACE.equals(element.getNamespaceURI())
                    && (IN.getLocalPart().equals(element.getLocalName())
                            || OUT.getLocalPart().equals(element.getLocalName()))) {
                final String source = element.getAttribute("source");
                final String target = element.getAttribute("target");
                if (source.isEmpty() || target.isEmpty()) {
                    throw new ProcessEngineException("Mapping " + element.getLocalName() + " of call activity '"
                            + call.getId() + "' needs the attributes source and target.");
                }
                mappings.add(new Mapping(IN.getLocalPart().equals(element.getLocalName()), source, target));
            }
        }
        return mappings;
    }

    private static Map<String, Object> input(final List<Mapping> mappings, final Map<String, Object> parent) {
        if (mappings.isEmpty()) {
            return new LinkedHashMap<>(parent);
        }
        final Map<String, Object> child = new LinkedHashMap<>();
        CallActivityInvoker.copy(mappings, true, parent, child);
        return child;
    }

    private static Map<String, Object> output(final List<Mapping> mappings, final Map<String, Object> parent,
            final Map<String, Object> child) {
        final Map<String, Object> result = new LinkedHashMap<>(parent);
        if (mappings.isEmpty()) {
            result.putAll(child);
        } else {
            CallActivityInvoker.copy(mappings, false, child, result);
        }
        return result;
    }

    private static void copy(final List<Mapping> mappings, final boolean input, final Map<String, Object> from,
            final Map<String, Object> to) {
        for (final Mapping mapping : mappings) {
            if (mapping.input() == input && from.containsKey(mapping.source())) {
                to.put(mapping.target(), from.get(mapping.source()));
            }
        }
    }

    /**
     * A {@link #IN} or {@link #OUT} variable mapping.
     *
     * @param input whether it is an {@code in} mapping
     * @param source the name of the variable to read
     * @param target the name of the variable to write
     */
    private record Mapping(boolean input, String source, String target) {
    }
}
