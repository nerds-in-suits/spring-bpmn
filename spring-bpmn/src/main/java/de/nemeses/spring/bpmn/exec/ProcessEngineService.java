package de.nemeses.spring.bpmn.exec;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.annotation.Transactional;

import de.nemeses.spring.bpmn.ProcessDefinitionRegistrar;
import de.nemeses.spring.bpmn.model.bpmn20.Definitions;
import de.nemeses.spring.bpmn.model.bpmn20.FlowNode;
import de.nemeses.spring.bpmn.model.bpmn20.Process;
import de.nemeses.spring.bpmn.model.bpmn20.RootElement;
import de.nemeses.spring.bpmn.model.bpmn20.StartEvent;
import de.nemeses.spring.bpmn.model.bpmn20.UserTask;
import de.nemeses.spring.bpmn.repository.NoSuchProcessDefinitionException;
import de.nemeses.spring.bpmn.repository.NoSuchProcessInstanceException;
import de.nemeses.spring.bpmn.repository.ProcessDefinition;
import de.nemeses.spring.bpmn.repository.ProcessDefinitionService;
import de.nemeses.spring.bpmn.repository.ProcessInstance;
import de.nemeses.spring.bpmn.repository.ProcessInstanceRepository;
import de.nemeses.spring.bpmn.repository.ProcessStep;
import de.nemeses.spring.bpmn.repository.ProcessStepRepository;
import jakarta.xml.bind.JAXBElement;

/**
 * Core service of the process engine: starts process instances, lists and completes their open user tasks and
 * updates their variables.
 * <p>
 * Starting an instance creates it and triggers each start event of the process ({@link Invoker#trigger}, which
 * creates a {@link ProcessStep} and a {@code bpmnNodeTask} for it), in one transaction. Failures (unknown definition,
 * parse error, no matching process, no start event) throw {@link ProcessEngineException} and roll back.
 * <p>
 * An open user task is a pending step at a {@code userTask} node ({@link #openUserTasks}); it is identified by the id
 * of its step and completed with {@link #completeUserTask}.
 * <p>
 * The variables of a running instance are changed from outside with {@link #updateVariables}.
 * <p>
 * The {@link InvokerRegistry} is looked up when an instance is started and the {@link BpmnNodeExecutor} when a user
 * task is completed, because the invoker of {@code callActivity} depends on this service.
 */
public class ProcessEngineService {

    private final ProcessDefinitionService definitions;
    private final ProcessInstanceRepository instances;
    private final ProcessStepRepository steps;
    private final ProcessDefinitionModelCache models;
    private final ObjectProvider<InvokerRegistry> invokers;
    private final ObjectProvider<BpmnNodeExecutor> executor;

    ProcessEngineService(final ProcessDefinitionService definitions, final ProcessInstanceRepository instances,
            final ProcessStepRepository steps, final ProcessDefinitionModelCache models,
            final ObjectProvider<InvokerRegistry> invokers, final ObjectProvider<BpmnNodeExecutor> executor) {
        this.definitions = definitions;
        this.instances = instances;
        this.steps = steps;
        this.models = models;
        this.invokers = invokers;
        this.executor = executor;
    }

    /**
     * Starts a new instance of the latest version of the named process definition.
     *
     * @param name the process definition name
     * @param variables the initial process variables; {@code null} means none
     * @return the created instance
     * @throws ProcessEngineException if no definition of that name exists or the instance cannot be started
     */
    @Transactional(rollbackFor = ProcessEngineException.class)
    public ProcessInstance startProcess(final String name, final Map<String, Object> variables) throws ProcessEngineException {
        try {
            final ProcessDefinition definition = this.definitions.retrieveLatest(name);
            return this.startProcess(definition, variables);
        } catch (final NoSuchProcessDefinitionException e) {
            throw new ProcessEngineException("Cannot start process '" + name + "'.", e);
        }
    }

    /**
     * Persists a new instance of the given definition and triggers each start event of the BPMN process whose name
     * (or id, if it has no name) equals the definition name.
     *
     * @param definition the definition version to start
     * @param variables the initial process variables; {@code null} means none
     * @return the created instance
     * @throws ProcessEngineException if the BPMN document cannot be parsed, does not contain the process or the
     *             process has no start event
     */
    @Transactional(rollbackFor = ProcessEngineException.class)
    public ProcessInstance startProcess(final ProcessDefinition definition, final Map<String, Object> variables)
            throws ProcessEngineException {
        return this.startProcess(definition, variables, null);
    }

    /**
     * Like {@link #startProcess(ProcessDefinition, Map)}, for an instance started by a {@code callActivity} step. Runs
     * in the caller's transaction.
     *
     * @param definition the definition version to start
     * @param variables the initial process variables; {@code null} means none
     * @param parentStep the call activity step of the parent instance that waits for the new instance, or {@code null}
     * @return the created instance
     * @throws ProcessEngineException if the BPMN document cannot be parsed, does not contain the process or the
     *             process has no start event
     */
    public ProcessInstance startProcess(final ProcessDefinition definition, final Map<String, Object> variables,
            final ProcessStep parentStep) throws ProcessEngineException {
        final Definitions document = this.models.document(definition);
        final Process process = ProcessEngineService.process(document, definition);
        final Collection<FlowNode> startNodes = ProcessEngineService.startNodes(process, definition);
        final ProcessInstance instance = this.instances.create(definition, variables == null ? Map.of() : variables,
                parentStep);
        final InvokerRegistry registry = this.invokers.getObject();
        for (final FlowNode startNode : startNodes) {
            registry.trigger(instance.id(), process, document.getExpressionLanguage(), startNode, null, null);
        }
        LoggerFactory.getLogger(this.getClass()).info("Started process instance {} of {} at start nodes {}",
                instance.id(), definition.name(), startNodes.stream().map(FlowNode::getId).toList());
        return instance;
    }

    /**
     * Lists the open user tasks of a process instance: its pending steps at {@code userTask} nodes, including those
     * inside embedded sub-processes.
     *
     * @param processInstanceId the id of the process instance
     * @return the pending user task steps, in no particular order; empty if the instance has ended
     * @throws ProcessEngineException if the instance does not exist or its definition cannot be read
     */
    @Transactional(readOnly = true, rollbackFor = ProcessEngineException.class)
    public Collection<ProcessStep> openUserTasks(final UUID processInstanceId) throws ProcessEngineException {
        try {
            final ProcessInstance instance = this.instances.retrieve(processInstanceId);
            if (instance.endDate() != null) {
                return List.of();
            }
            final Process process = ProcessEngineService.process(this.models.document(instance.processDefinition()),
                    instance.processDefinition());
            final Set<String> userTasks = ProcessNavigator.flowNodeIds(process, UserTask.class);
            return this.steps.steps(processInstanceId).stream()
                    .filter(step -> step.endDate() == null && userTasks.contains(step.nodeId()))
                    .toList();
        } catch (final NoSuchProcessInstanceException e) {
            throw new ProcessEngineException("Cannot list the user tasks of process instance " + processInstanceId
                    + ".", e);
        }
    }

    /**
     * Completes an open user task: merges the given variables into the process variables, completes its step and
     * takes the outgoing flows of the {@code userTask}, in one transaction under the lock of the instance (see
     * {@link BpmnNodeExecutor#completeUserTask}). Failures roll back, so the user task stays open.
     *
     * @param stepId the id of the pending user task step (see {@link #openUserTasks})
     * @param variables the variables to set (added or replaced, none removed); {@code null} means none; values must
     *            not be {@code null}
     * @throws ProcessEngineException if the step does not exist, is already completed, is not at a {@code userTask},
     *             its instance has ended, a value is {@code null}, or the outgoing flows cannot be taken
     */
    @Transactional(rollbackFor = ProcessEngineException.class)
    public void completeUserTask(final UUID stepId, final Map<String, Object> variables)
            throws ProcessEngineException {
        this.executor.getObject().completeUserTask(stepId, variables);
    }

    /**
     * Updates the variables of a running process instance from outside, in one transaction.
     * <p>
     * In merge mode ({@code replace == false}) every entry of {@code variables} adds or replaces the variable of its
     * name and a {@code null} value removes it (like a JSON Merge Patch, RFC 7396, but only at the top level: a map
     * value replaces the old value as a whole); all other variables stay. In replace mode {@code variables} becomes the
     * complete set of variables, and {@code null} values are rejected.
     * <p>
     * The update does <em>not</em> take the lock of the instance ({@link ProcessInstanceRepository#lock}), so it may
     * interleave with a step executing at the same time: such a step works on the variables it read before and may
     * overwrite this update when it stores its own changes. Nothing is re-evaluated; waiting steps see the new values
     * when they execute.
     *
     * @param processInstanceId the id of the process instance
     * @param variables the variables to merge, or the new complete set; {@code null} means none, so it changes nothing
     *            in merge mode and removes all variables in replace mode; keys must not be {@code null}
     * @param replace {@code true} to replace all variables, {@code false} to merge
     * @return the updated instance
     * @throws ProcessEngineException if the instance does not exist (caused by a
     *             {@link NoSuchProcessInstanceException}) or has ended, a key is {@code null}, or a value is
     *             {@code null} in replace mode
     */
    @Transactional(rollbackFor = ProcessEngineException.class)
    public ProcessInstance updateVariables(final UUID processInstanceId, final Map<String, Object> variables,
            final boolean replace) throws ProcessEngineException {
        try {
            final ProcessInstance instance = this.instances.retrieve(processInstanceId);
            if (instance.endDate() != null) {
                throw new ProcessEngineException("Cannot update the variables of process instance "
                        + processInstanceId + ", it has ended.");
            }
            final Map<String, Object> changes = variables == null ? Map.of() : variables;
            if (!replace && changes.isEmpty()) {
                return instance;
            }
            final Map<String, Object> updated = ProcessEngineService.applyVariables(processInstanceId,
                    replace ? Map.of() : instance.variables(), changes, replace);
            LoggerFactory.getLogger(this.getClass()).info("Updating variables {} of process instance {}{}",
                    changes.keySet(), processInstanceId, replace ? ", replacing all" : "");
            return this.instances.updateVariables(processInstanceId, updated);
        } catch (final NoSuchProcessInstanceException e) {
            throw new ProcessEngineException("Cannot update the variables of process instance " + processInstanceId
                    + ".", e);
        }
    }

    /**
     * Applies variable changes as described in {@link #updateVariables}.
     *
     * @param processInstanceId the id of the process instance, for the messages
     * @param current the variables to start from (empty in replace mode)
     * @param changes the changes
     * @param replace whether {@code null} values are rejected instead of removing the variable
     * @return the resulting variables
     * @throws ProcessEngineException if a key is {@code null}, or a value is {@code null} in replace mode
     */
    private static Map<String, Object> applyVariables(final UUID processInstanceId, final Map<String, Object> current,
            final Map<String, Object> changes, final boolean replace) throws ProcessEngineException {
        final Map<String, Object> updated = new HashMap<>(current);
        for (final Map.Entry<String, Object> variable : changes.entrySet()) {
            if (variable.getKey() == null) {
                throw new ProcessEngineException("Cannot update the variables of process instance "
                        + processInstanceId + " with a null variable name.");
            }
            if (variable.getValue() != null) {
                updated.put(variable.getKey(), variable.getValue());
            } else if (replace) {
                throw new ProcessEngineException("Cannot replace the variables of process instance "
                        + processInstanceId + " with the null value of " + variable.getKey() + ".");
            } else {
                updated.remove(variable.getKey());
            }
        }
        return updated;
    }

    private static Collection<FlowNode> startNodes(final Process process, final ProcessDefinition definition)
            throws ProcessEngineException {
        final Collection<FlowNode> startNodes = process.getFlowElement().stream()
                .map(JAXBElement::getValue)
                .filter(StartEvent.class::isInstance)
                .map(FlowNode.class::cast)
                .toList();
        if (startNodes.isEmpty()) {
            throw new ProcessEngineException("Process definition '" + definition.name() + "' has no start event.");
        }
        return startNodes;
    }

    /**
     * Selects the process whose name (or id, if it has no name) equals the definition name.
     *
     * @param document the parsed document of the definition
     * @param definition the definition
     * @return the process
     * @throws ProcessEngineException if the document does not contain the process
     */
    static Process process(final Definitions document, final ProcessDefinition definition)
            throws ProcessEngineException {
        for (final JAXBElement<? extends RootElement> element : document.getRootElement()) {
            if (element.getValue() instanceof final Process process
                    && definition.name().equals(ProcessDefinitionRegistrar.processName(process))) {
                return process;
            }
        }
        throw new ProcessEngineException("Process definition '" + definition.name() + "' contains no process of that name.");
    }
}
