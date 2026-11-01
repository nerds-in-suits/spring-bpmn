package de.nemeses.spring.bpmn.exec;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import de.nemeses.spring.bpmn.ProcessDefinitionRegistrar;
import de.nemeses.spring.bpmn.model.bpmn20.Definitions;
import de.nemeses.spring.bpmn.model.bpmn20.FlowElement;
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
import de.nemeses.spring.tasks.TaskService;
import jakarta.xml.bind.JAXBElement;

/**
 * Core service of the process engine: starts process instances and completes their open user tasks.
 * <p>
 * Starting an instance creates it, a {@link ProcessStep} for each start event of the process and a
 * {@link BpmnNodeTask} for each step, in one transaction. Failures (unknown definition, parse error, no matching
 * process, no start event) throw {@link ProcessEngineException} and roll back.
 * <p>
 * An open user task is a pending step at a {@code userTask} node ({@link #openUserTasks}); it is identified by the id
 * of its step and completed with {@link #completeUserTask}.
 */
@Component
public class ProcessEngineService {

    private final ProcessDefinitionService definitions;
    private final ProcessInstanceRepository instances;
    private final ProcessStepRepository steps;
    private final ProcessDefinitionModelCache models;
    private final TaskService taskService;
    private final ObjectProvider<BpmnNodeExecutor> executor;

    // the executor is looked up lazily: it depends on the invokers, and the call activity invoker on this service
    ProcessEngineService(final ProcessDefinitionService definitions, final ProcessInstanceRepository instances,
            final ProcessStepRepository steps, final ProcessDefinitionModelCache models, final TaskService taskService,
            final ObjectProvider<BpmnNodeExecutor> executor) {
        this.definitions = definitions;
        this.instances = instances;
        this.steps = steps;
        this.models = models;
        this.taskService = taskService;
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
     * Persists a new instance of the given definition and creates a step and triggers a {@link BpmnNodeTask} for each
     * start event of the BPMN process whose name (or id, if it has no name) equals the definition name.
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
        try {
            final Collection<String> startNodes = this.startNodes(definition);
            final ProcessInstance instance = this.instances.create(definition, variables == null ? Map.of() : variables,
                    parentStep);
            for (final String nodeId : startNodes) {
                BpmnNodeExecutor.schedule(this.steps, this.taskService, instance.id(), nodeId, null, null);
            }
            LoggerFactory.getLogger(this.getClass()).info("Started process instance {} of {} at start nodes {}",
                    instance.id(), definition.name(), startNodes);
            return instance;
        } catch (final NoSuchProcessInstanceException e) {
            throw new IllegalStateException("Created process instance not found for start nodes.", e);
        }
    }

    /**
     * Lists the open user tasks of a process instance: its pending steps at {@code userTask} nodes, including those
     * inside embedded sub-processes.
     *
     * @param processInstanceId the id of the process instance
     * @return the pending user task steps, ordered by start date (creation of the user task); empty if the instance
     *         has ended
     * @throws ProcessEngineException if the instance does not exist or its definition cannot be read
     */
    @Transactional(readOnly = true, rollbackFor = ProcessEngineException.class)
    public List<ProcessStep> openUserTasks(final UUID processInstanceId) throws ProcessEngineException {
        final ProcessInstance instance;
        try {
            instance = this.instances.retrieve(processInstanceId);
        } catch (final NoSuchProcessInstanceException e) {
            throw new ProcessEngineException("Cannot list the user tasks of process instance " + processInstanceId
                    + ".", e);
        }
        if (instance.endDate() != null) {
            return List.of();
        }
        final Process process = ProcessEngineService.process(this.models.document(instance.processDefinition()),
                instance.processDefinition());
        final List<ProcessStep> open = new ArrayList<>();
        for (final ProcessStep step : this.steps.steps(processInstanceId)) {
            if (step.endDate() == null
                    && ProcessNavigator.flowNode(process, step.nodeId()).getClass() == UserTask.class) {
                open.add(step);
            }
        }
        return open;
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

    private Collection<String> startNodes(final ProcessDefinition definition) throws ProcessEngineException {
        final Process process = ProcessEngineService.process(this.models.document(definition), definition);
        final Collection<String> startNodes = process.getFlowElement().stream()
                .map(JAXBElement::getValue)
                .filter(StartEvent.class::isInstance)
                .map(FlowElement::getId)
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
