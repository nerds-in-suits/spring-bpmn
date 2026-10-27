package de.nemeses.spring.bpmn;

import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.xml.namespace.QName;

import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import de.nemeses.spring.bpmn.model.bpmn20.Activity;
import de.nemeses.spring.bpmn.model.bpmn20.EndEvent;
import de.nemeses.spring.bpmn.model.bpmn20.ExclusiveGateway;
import de.nemeses.spring.bpmn.model.bpmn20.FlowElement;
import de.nemeses.spring.bpmn.model.bpmn20.FlowNode;
import de.nemeses.spring.bpmn.model.bpmn20.ParallelGateway;
import de.nemeses.spring.bpmn.model.bpmn20.Process;
import de.nemeses.spring.bpmn.model.bpmn20.SequenceFlow;
import de.nemeses.spring.bpmn.model.bpmn20.ServiceTask;
import de.nemeses.spring.bpmn.model.bpmn20.StartEvent;
import de.nemeses.spring.bpmn.model.bpmn20.Task;
import de.nemeses.spring.bpmn.repository.NoSuchProcessInstanceException;
import de.nemeses.spring.bpmn.repository.ProcessInstance;
import de.nemeses.spring.bpmn.repository.ProcessInstanceRepository;
import de.nemeses.spring.tasks.TaskService;
import jakarta.xml.bind.JAXBElement;

/**
 * Executes single nodes of process instances; called by the {@link BpmnNodeTask}.
 */
@Component
public class BpmnNodeExecutor {

    /** Namespace of the BPMN extension attributes understood by the engine. */
    public static final String NAMESPACE = "https://nemeses.de/spring-bpmn";

    /** Extension attribute of a {@code serviceTask} naming its {@link BpmnNodeImplementation} bean. */
    public static final QName BEAN_ATTRIBUTE = new QName(BpmnNodeExecutor.NAMESPACE, "bean");

    private final ProcessInstanceRepository instances;
    private final BpmnParser parser;
    private final TaskService taskService;
    private final Map<String, BpmnNodeImplementation> implementations;

    BpmnNodeExecutor(final ProcessInstanceRepository instances, final BpmnParser parser, final TaskService taskService,
            final Map<String, BpmnNodeImplementation> implementations) {
        this.instances = instances;
        this.parser = parser;
        this.taskService = taskService;
        this.implementations = implementations;
    }

    /**
     * Executes one node of a process instance and schedules a {@link BpmnNodeTask} for the target of each sequence
     * flow taken, all in one transaction. Supported node types:
     * <ul>
     * <li>{@code startEvent}, {@code task}: nothing to do, all outgoing flows are taken.</li>
     * <li>{@code serviceTask}: calls the {@link BpmnNodeImplementation} bean named by the extension attribute
     * {@link #BEAN_ATTRIBUTE}, then takes all outgoing flows.</li>
     * <li>{@code exclusiveGateway}: takes the {@code default} flow if set, else the only outgoing flow.</li>
     * <li>{@code parallelGateway}: takes all outgoing flows (forks; joining is not synchronized yet).</li>
     * <li>{@code endEvent} without event definition: ends the instance (sets its end date).</li>
     * </ul>
     * Except for the default flow of an exclusive gateway, a sequence flow to take must not have a condition; activities
     * must not have loop characteristics.
     * Nodes of an instance that has already ended are skipped.
     *
     * @param processId the id of the process instance
     * @param nodeId the id of the flow node to execute
     * @throws ProcessEngineException if the instance or node does not exist, the node type or a sequence flow
     *             condition is not supported, or the node's implementation failed
     */
    @Transactional(rollbackFor = ProcessEngineException.class)
    public void executeNode(final UUID processId, final String nodeId) throws ProcessEngineException {
        try {
            final ProcessInstance instance = this.instances.retrieve(processId);
            if (instance.endDate() != null) {
                LoggerFactory.getLogger(this.getClass()).warn(
                    "Skipping node {} of process instance {}, which has ended.",
                        nodeId, processId);
                return;
            }
            final Process process = ProcessEngineService.process(this.parser, instance.processDefinition());
            final List<SequenceFlow> taken = this.run(instance, process, BpmnNodeExecutor.flowNode(process, nodeId));
            for (final SequenceFlow flow : taken) {
                final String target = ((FlowElement) flow.getTargetRef()).getId();
                this.taskService.trigger(BpmnNodeTask.BEAN_NAME, new NodeTaskPayload(processId, target));
            }
            LoggerFactory.getLogger(this.getClass()).debug("Executed node {} of process instance {}, taken flows {}",
                    nodeId, processId, taken.stream().map(SequenceFlow::getId).toList());
        } catch (final NoSuchProcessInstanceException e) {
            throw new ProcessEngineException("Cannot execute node '" + nodeId + "'.", e);
        }
    }

    /**
     * Runs the node according to its type.
     *
     * @return the sequence flows to take
     */
    private List<SequenceFlow> run(final ProcessInstance instance, final Process process, final FlowNode node)
            throws ProcessEngineException {
        if (node instanceof final Activity activity && activity.getLoopCharacteristics() != null) {
            throw new ProcessEngineException("Activity '" + node.getId()
                    + "' has loop characteristics, which are not supported yet.");
        }
        final List<SequenceFlow> outgoing = BpmnNodeExecutor.outgoing(process, node);
        return switch (node) {
        case StartEvent _, ParallelGateway _ -> BpmnNodeExecutor.unconditional(outgoing);
        case final EndEvent event -> {
            this.end(instance, event);
            yield List.of();
        }
        case final ServiceTask task -> {
            this.implementation(task).execute(new NodeContext(instance.id(), task.getId(), instance.variables()));
            yield BpmnNodeExecutor.unconditional(outgoing);
        }
        case final ExclusiveGateway gateway -> List.of(BpmnNodeExecutor.exclusive(gateway, outgoing));
        case final Task task when task.getClass() == Task.class -> BpmnNodeExecutor.unconditional(outgoing);
        default -> throw new ProcessEngineException("Node '" + node.getId() + "' is a "
                + node.getClass().getSimpleName() + ", which is not supported yet.");
        };
    }

    private void end(final ProcessInstance instance, final EndEvent event) throws ProcessEngineException {
        if (!event.getEventDefinition().isEmpty() || !event.getEventDefinitionRef().isEmpty()) {
            throw new ProcessEngineException("End event '" + event.getId()
                    + "' has an event definition, which is not supported yet.");
        }
        try {
            this.instances.updateEndDate(instance.id(), new Date());
        } catch (final NoSuchProcessInstanceException e) {
            throw new ProcessEngineException("Cannot end process instance " + instance.id() + ".", e);
        }
        LoggerFactory.getLogger(this.getClass()).info("Process instance {} ended at {}", instance.id(), event.getId());
    }

    private BpmnNodeImplementation implementation(final ServiceTask task) throws ProcessEngineException {
        final String beanName = task.getOtherAttributes().get(BpmnNodeExecutor.BEAN_ATTRIBUTE);
        if (beanName == null) {
            throw new ProcessEngineException("Service task '" + task.getId() + "' has no attribute " + BpmnNodeExecutor.BEAN_ATTRIBUTE
                    + ".");
        }
        final BpmnNodeImplementation implementation = this.implementations.get(beanName);
        if (implementation == null) {
            throw new ProcessEngineException("Service task '" + task.getId() + "' refers to bean '" + beanName
                    + "', which is no BpmnNodeImplementation.");
        }
        return implementation;
    }

    private static SequenceFlow exclusive(final ExclusiveGateway gateway, final List<SequenceFlow> outgoing)
            throws ProcessEngineException {
        if (gateway.getDefault() instanceof final SequenceFlow defaultFlow) {
            return defaultFlow;
        }
        if (outgoing.size() == 1) {
            return BpmnNodeExecutor.unconditional(outgoing).get(0);
        }
        throw new ProcessEngineException("Exclusive gateway '" + gateway.getId() + "' has " + outgoing.size()
                + " outgoing flows and no default flow; conditions are not supported yet.");
    }

    private static List<SequenceFlow> unconditional(final List<SequenceFlow> flows) throws ProcessEngineException {
        for (final SequenceFlow flow : flows) {
            if (flow.getConditionExpression() != null) {
                throw new ProcessEngineException("Sequence flow '" + flow.getId()
                        + "' has a condition, which is not supported yet.");
            }
        }
        return flows;
    }

    private static List<SequenceFlow> outgoing(final Process process, final FlowNode node) {
        return process.getFlowElement().stream()
                .map(JAXBElement::getValue)
                .filter(SequenceFlow.class::isInstance)
                .map(SequenceFlow.class::cast)
                .filter(flow -> flow.getSourceRef() instanceof final FlowElement source
                        && node.getId().equals(source.getId()))
                .toList();
    }

    private static FlowNode flowNode(final Process process, final String nodeId) throws ProcessEngineException {
        for (final JAXBElement<? extends FlowElement> element : process.getFlowElement()) {
            if (element.getValue() instanceof final FlowNode node && nodeId.equals(node.getId())) {
                return node;
            }
        }
        throw new ProcessEngineException("Process '" + ProcessDefinitionRegistrar.processName(process)
                + "' has no flow node '" + nodeId + "'.");
    }

    /**
     * The {@link BpmnNodeContext} passed to a {@link BpmnNodeImplementation}.
     *
     * @param processInstanceId the process instance id
     * @param nodeId the node id
     * @param variables the process variables
     */
    private record NodeContext(UUID processInstanceId, String nodeId, Map<String, Object> variables)
            implements BpmnNodeContext {

        NodeContext {
            variables = Collections.unmodifiableMap(new LinkedHashMap<>(variables));
        }
    }
}
