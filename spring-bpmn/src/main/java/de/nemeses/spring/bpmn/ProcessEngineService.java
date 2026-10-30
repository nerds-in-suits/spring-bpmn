package de.nemeses.spring.bpmn;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import de.nemeses.spring.bpmn.model.bpmn20.Definitions;
import de.nemeses.spring.bpmn.model.bpmn20.FlowElement;
import de.nemeses.spring.bpmn.model.bpmn20.Process;
import de.nemeses.spring.bpmn.model.bpmn20.RootElement;
import de.nemeses.spring.bpmn.model.bpmn20.StartEvent;
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
 * Core service of the process engine: starts process instances.
 * <p>
 * Starting an instance creates it, a {@link ProcessStep} for each start event of the process and a
 * {@link BpmnNodeTask} for each step, in one transaction. Failures (unknown definition, parse error, no matching
 * process, no start event) throw {@link ProcessEngineException} and roll back.
 */
@Component
public class ProcessEngineService {

    private final ProcessDefinitionService definitions;
    private final ProcessInstanceRepository instances;
    private final ProcessStepRepository steps;
    private final BpmnParser parser;
    private final TaskService taskService;

    ProcessEngineService(final ProcessDefinitionService definitions, final ProcessInstanceRepository instances,
            final ProcessStepRepository steps, final BpmnParser parser, final TaskService taskService) {
        this.definitions = definitions;
        this.instances = instances;
        this.steps = steps;
        this.parser = parser;
        this.taskService = taskService;
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
    ProcessInstance startProcess(final ProcessDefinition definition, final Map<String, Object> variables,
            final ProcessStep parentStep) throws ProcessEngineException {
        final List<String> startNodes = this.startNodes(definition);
        final ProcessInstance instance = this.instances.create(definition, variables == null ? Map.of() : variables,
                parentStep);
        try {
            for (final String nodeId : startNodes) {
                BpmnNodeExecutor.schedule(this.steps, this.taskService, instance.id(), nodeId, null, null);
            }
        } catch (final NoSuchProcessInstanceException e) {
            throw new ProcessEngineException("Cannot start process instance " + instance.id() + ".", e);
        }
        LoggerFactory.getLogger(this.getClass()).info("Started process instance {} of {} at start nodes {}",
                instance.id(), definition.name(), startNodes);
        return instance;
    }

    private List<String> startNodes(final ProcessDefinition definition) throws ProcessEngineException {
        final Process process = ProcessEngineService.process(this.parser, definition);
        final List<String> startNodes = process.getFlowElement().stream()
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
     * Parses the BPMN document of a definition and selects the process whose name (or id, if it has no name) equals
     * the definition name.
     *
     * @param parser the parser
     * @param definition the definition
     * @return the process
     * @throws ProcessEngineException if the document cannot be parsed or does not contain the process
     */
    static Process process(final BpmnParser parser, final ProcessDefinition definition) throws ProcessEngineException {
        return ProcessEngineService.process(ProcessEngineService.parse(parser, definition), definition);
    }

    /**
     * Parses the BPMN document of a definition.
     *
     * @param parser the parser
     * @param definition the definition
     * @return the {@code definitions} element of the document
     * @throws ProcessEngineException if the document cannot be parsed
     */
    static Definitions parse(final BpmnParser parser, final ProcessDefinition definition)
            throws ProcessEngineException {
        try {
            return parser.parse(new ByteArrayInputStream(definition.bpmnXml().getBytes(StandardCharsets.UTF_8)));
        } catch (final BpmnParseException e) {
            throw new ProcessEngineException("Cannot parse process definition '" + definition.name() + "'.", e);
        }
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
