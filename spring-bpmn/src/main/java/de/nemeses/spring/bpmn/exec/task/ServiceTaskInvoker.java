package de.nemeses.spring.bpmn.exec.task;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import de.nemeses.spring.bpmn.exec.BpmnNodeContext;
import de.nemeses.spring.bpmn.exec.BpmnNodeExecutor;
import de.nemeses.spring.bpmn.exec.BpmnNodeImplementation;
import de.nemeses.spring.bpmn.exec.FlowSelector;
import de.nemeses.spring.bpmn.exec.Invoker;
import de.nemeses.spring.bpmn.exec.NodeExecution;
import de.nemeses.spring.bpmn.exec.ProcessEngineException;
import de.nemeses.spring.bpmn.model.bpmn20.SequenceFlow;
import de.nemeses.spring.bpmn.model.bpmn20.ServiceTask;

/**
 * Executes a {@code serviceTask}: calls the {@link BpmnNodeImplementation} bean named by the extension attribute
 * {@link BpmnNodeExecutor#BEAN_ATTRIBUTE} (once, or per iteration of its standard loop, through the
 * {@link TaskRunner}), stores the variables it changed through its {@link BpmnNodeContext}, completes the step and
 * takes the outgoing flows like for every activity ({@link FlowSelector#activity}), evaluating their conditions
 * against the changed variables. A multi-instance loop runs through the {@link MultiInstanceLoop}, with one step per
 * instance.
 */
@Component
class ServiceTaskInvoker implements Invoker<ServiceTask> {

    private final TaskRunner runner;
    private final FlowSelector flows;
    private final Map<String, BpmnNodeImplementation> implementations;
    private final MultiInstanceLoop multiInstances;

    ServiceTaskInvoker(final TaskRunner runner, final FlowSelector flows,
            final Map<String, BpmnNodeImplementation> implementations, final MultiInstanceLoop multiInstances) {
        this.runner = runner;
        this.flows = flows;
        this.implementations = implementations;
        this.multiInstances = multiInstances;
    }

    @Override
    public Class<ServiceTask> nodeType() {
        return ServiceTask.class;
    }

    @Override
    public List<SequenceFlow> invoke(final ServiceTask node, final NodeExecution execution)
            throws ProcessEngineException {
        final BpmnNodeImplementation implementation = this.implementation(node);
        if (MultiInstanceLoop.isMultiInstance(node)) {
            return this.multiInstances.execute(node, execution, implementation::execute);
        }
        final Map<String, Object> variables = this.runner.run(node, execution, implementation::execute);
        final List<SequenceFlow> taken = this.flows.activity(node, execution.outgoing(), execution.language(),
                variables);
        execution.complete();
        return taken;
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
