package de.nemeses.spring.bpmn.exec;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import de.nemeses.spring.bpmn.model.bpmn20.Definitions;
import de.nemeses.spring.bpmn.model.bpmn20.FlowNode;
import de.nemeses.spring.bpmn.model.bpmn20.Process;
import de.nemeses.spring.bpmn.repository.ProcessInstance;
import de.nemeses.spring.bpmn.repository.ProcessInstanceRepository;
import de.nemeses.spring.bpmn.repository.ProcessStep;
import de.nemeses.spring.bpmn.repository.ProcessStepRepository;
import de.nemeses.spring.tasks.TaskMdc;
import de.nemeses.spring.tasks.TaskService;

/**
 * Looks up the {@link Invoker} for a flow node and triggers nodes through it. All {@code Invoker} beans are injected
 * and looked up by the exact class of the node ({@link Invoker#nodeType()}); each is passed through all
 * {@link InvokerEnhancer} beans in turn. Used by the {@link BpmnNodeExecutor}, the {@link ProcessEngineService} and
 * {@link NodeTrigger#trigger(String, String, UUID)}, so invokers trigger other nodes without depending on this bean.
 */
@Component
class InvokerRegistry {

    private final ProcessInstanceRepository instanceRepository;
    private final ProcessStepRepository stepRepository;
    private final TaskService tasks;
    private final ProcessDefinitionModelCache models;
    private final Map<Class<? extends FlowNode>, Invoker<?>> invokers;

    InvokerRegistry(final ProcessInstanceRepository instances, final ProcessStepRepository steps,
            final TaskService taskService, final ProcessDefinitionModelCache models,
            final Collection<Invoker<?>> invokers, final Collection<InvokerEnhancer> enhancers) {
        this.instanceRepository = instances;
        this.stepRepository = steps;
        this.tasks = taskService;
        this.models = models;
        this.invokers = InvokerRegistry.byNodeType(invokers, enhancers);
    }

    /**
     * @return the invokers by their node type, each enhanced by all enhancers in turn
     * @throws IllegalStateException if two invokers execute the same node type
     */
    private static Map<Class<? extends FlowNode>, Invoker<?>> byNodeType(final Collection<Invoker<?>> invokers,
            final Collection<InvokerEnhancer> enhancers) {
        final Map<Class<? extends FlowNode>, Invoker<?>> byType = new HashMap<>();
        for (final Invoker<?> invoker : invokers) {
            final Invoker<?> other = byType.putIfAbsent(invoker.nodeType(), invoker);
            if (other != null) {
                throw new IllegalStateException("Invokers " + other.getClass().getName() + " and "
                        + invoker.getClass().getName() + " both execute " + invoker.nodeType().getSimpleName()
                        + ".");
            }
        }
        byType.replaceAll((_, invoker) -> InvokerRegistry.enhance(invoker, enhancers));
        return Map.copyOf(byType);
    }

    private static <T extends FlowNode> Invoker<T> enhance(final Invoker<T> invoker,
            final Collection<InvokerEnhancer> enhancers) {
        Invoker<T> enhanced = invoker;
        for (final InvokerEnhancer enhancer : enhancers) {
            enhanced = enhancer.enhance(enhanced);
        }
        return enhanced;
    }

    ProcessInstanceRepository instances() {
        return this.instanceRepository;
    }

    ProcessStepRepository steps() {
        return this.stepRepository;
    }

    TaskService taskService() {
        return this.tasks;
    }

    /**
     * @param node a flow node
     * @return the (enhanced) invoker for the exact class of the node
     * @throws ProcessEngineException if the node type is not supported
     */
    Invoker<?> invoker(final FlowNode node) throws ProcessEngineException {
        final Invoker<?> invoker = this.invokers.get(node.getClass());
        if (invoker == null) {
            throw new ProcessEngineException("Node '" + node.getId() + "' is a " + node.getClass().getSimpleName()
                    + ", which is not supported yet.");
        }
        return invoker;
    }

    /**
     * Triggers a node of an instance, reading the process from the definition of the instance.
     *
     * @param instance the process instance
     * @param nodeId the id of the flow node reached
     * @param incomingFlowId the id of the sequence flow taken to the node, {@code null} for a start event
     * @param parentStepId the scope: the step of the embedded sub-process the node runs in, {@code null} at the top
     *            level
     * @return the step created for the node
     * @throws ProcessEngineException if the definition or node does not exist or the node cannot be triggered
     */
    ProcessStep trigger(final ProcessInstance instance, final String nodeId, final String incomingFlowId,
            final UUID parentStepId) throws ProcessEngineException {
        final Definitions document = this.models.document(instance.processDefinition());
        final Process process = ProcessEngineService.process(document, instance.processDefinition());
        return this.trigger(instance.id(), process, document.getExpressionLanguage(),
                ProcessNavigator.flowNode(process, nodeId), incomingFlowId, parentStepId);
    }

    /**
     * Triggers a node through its {@link Invoker} ({@link Invoker#trigger}), with the instance id in the MDC as
     * {@link ProcessMdc#PROCESS_INSTANCE_ID} meanwhile (so also when {@link ProcessEngineService} starts an instance);
     * the previous value is restored afterwards.
     *
     * @param instanceId the id of the process instance
     * @param process the process
     * @param language the default expression language of the document
     * @param node the flow node reached
     * @param incomingFlowId the id of the sequence flow taken to the node, {@code null} for a start event
     * @param parentStepId the scope: the step of the embedded sub-process the node runs in, {@code null} at the top
     *            level
     * @return the step created for the node
     * @throws ProcessEngineException if the node type is not supported or the node cannot be triggered
     */
    ProcessStep trigger(final UUID instanceId, final Process process, final String language, final FlowNode node,
            final String incomingFlowId, final UUID parentStepId) throws ProcessEngineException {
        try (TaskMdc.Scope _ = ProcessMdc.put(instanceId)) {
            return InvokerRegistry.trigger(this.invoker(node),
                    this.nodeTrigger(instanceId, process, language, node, incomingFlowId, parentStepId));
        }
    }

    private static <T extends FlowNode> ProcessStep trigger(final Invoker<T> invoker, final NodeTrigger trigger)
            throws ProcessEngineException {
        return invoker.trigger(invoker.nodeType().cast(trigger.node()), trigger);
    }

    /**
     * @return the trigger of a node without loop data
     */
    NodeTrigger nodeTrigger(final UUID instanceId, final Process process, final String language, final FlowNode node,
            final String incomingFlowId, final UUID parentStepId) {
        return new NodeTrigger(this, instanceId, process, language, node, incomingFlowId, parentStepId);
    }
}
