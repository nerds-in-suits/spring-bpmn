package de.nemeses.spring.bpmn;

import java.util.Map;

import org.springframework.stereotype.Component;

import de.nemeses.spring.bpmn.model.bpmn20.ServiceTask;
import de.nemeses.spring.bpmn.repository.NoSuchProcessInstanceException;
import de.nemeses.spring.bpmn.repository.ProcessInstance;
import de.nemeses.spring.bpmn.repository.ProcessInstanceRepository;

/**
 * Calls the {@link BpmnNodeImplementation} of a {@code serviceTask} for the {@link BpmnNodeExecutor} and stores the
 * variables it changed.
 */
@Component
class ServiceTaskInvoker {

    private final ProcessInstanceRepository instances;
    private final Map<String, BpmnNodeImplementation> implementations;

    ServiceTaskInvoker(final ProcessInstanceRepository instances,
            final Map<String, BpmnNodeImplementation> implementations) {
        this.instances = instances;
        this.implementations = implementations;
    }

    /**
     * Calls the {@link BpmnNodeImplementation} bean named by the extension attribute
     * {@link BpmnNodeExecutor#BEAN_ATTRIBUTE} and stores the variables it changed through its {@link BpmnNodeContext}
     * ({@link ProcessInstanceRepository#updateVariables}, only if something changed).
     *
     * @param instance the (locked) process instance
     * @param task the service task
     * @return the variables including the changes of the task
     * @throws ProcessEngineException if the bean is missing or no {@link BpmnNodeImplementation}, the implementation
     *             failed or the variables cannot be stored
     */
    Map<String, Object> invoke(final ProcessInstance instance, final ServiceTask task) throws ProcessEngineException {
        final NodeContext context = new NodeContext(instance.id(), task.getId(), instance.variables());
        this.implementation(task).execute(context);
        if (context.isChanged()) {
            try {
                this.instances.updateVariables(context.processInstanceId(), context.changedVariables());
            } catch (final NoSuchProcessInstanceException e) {
                throw new ProcessEngineException("Cannot store the variables of process instance "
                        + context.processInstanceId() + ".", e);
            }
        }
        return context.changedVariables();
    }

    private BpmnNodeImplementation implementation(final ServiceTask task) throws ProcessEngineException {
        final String beanName = task.getOtherAttributes().get(BpmnNodeExecutor.BEAN_ATTRIBUTE);
        if (beanName == null) {
            throw new ProcessEngineException("Service task '" + task.getId() + "' has no attribute "
                    + BpmnNodeExecutor.BEAN_ATTRIBUTE + ".");
        }
        final BpmnNodeImplementation implementation = this.implementations.get(beanName);
        if (implementation == null) {
            throw new ProcessEngineException("Service task '" + task.getId() + "' refers to bean '" + beanName
                    + "', which is no BpmnNodeImplementation.");
        }
        return implementation;
    }
}
