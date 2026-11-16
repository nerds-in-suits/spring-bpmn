package de.nemeses.spring.bpmn.exec;

import java.util.Collection;
import java.util.Map;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import de.nemeses.spring.bpmn.ProcessDefinitionRegistrar;
import de.nemeses.spring.bpmn.model.bpmn20.Definitions;
import de.nemeses.spring.bpmn.model.bpmn20.FlowNode;
import de.nemeses.spring.bpmn.model.bpmn20.Process;
import de.nemeses.spring.bpmn.model.bpmn20.RootElement;
import de.nemeses.spring.bpmn.model.bpmn20.StartEvent;
import de.nemeses.spring.bpmn.repository.NoSuchProcessDefinitionException;
import de.nemeses.spring.bpmn.repository.ProcessDefinition;
import de.nemeses.spring.bpmn.repository.ProcessDefinitionService;
import de.nemeses.spring.bpmn.repository.ProcessInstance;
import de.nemeses.spring.bpmn.repository.ProcessInstanceRepository;
import de.nemeses.spring.bpmn.repository.ProcessStep;
import jakarta.xml.bind.JAXBElement;

/**
 * Core service of the process engine: starts process instances.
 * <p>
 * Starting an instance creates it and triggers each start event of the process ({@link Invoker#trigger}, which
 * creates a {@link ProcessStep} and a {@code bpmnNodeTask} for it), in one transaction. Failures (unknown definition,
 * parse error, no matching process, no start event) throw {@link ProcessEngineException} and roll back.
 * <p>
 * The {@link InvokerRegistry} is looked up when an instance is started, because the invoker of {@code callActivity}
 * depends on this service.
 */
@Component
public class ProcessEngineService {

    private final ProcessDefinitionService definitions;
    private final ProcessInstanceRepository instances;
    private final ProcessDefinitionModelCache models;
    private final ObjectProvider<InvokerRegistry> invokers;

    ProcessEngineService(final ProcessDefinitionService definitions, final ProcessInstanceRepository instances,
            final ProcessDefinitionModelCache models, final ObjectProvider<InvokerRegistry> invokers) {
        this.definitions = definitions;
        this.instances = instances;
        this.models = models;
        this.invokers = invokers;
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
