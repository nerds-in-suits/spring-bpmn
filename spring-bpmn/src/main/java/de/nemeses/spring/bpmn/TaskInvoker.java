package de.nemeses.spring.bpmn;

import java.util.Map;

import org.springframework.stereotype.Component;

import de.nemeses.spring.bpmn.model.bpmn20.Activity;
import de.nemeses.spring.bpmn.model.bpmn20.ScriptTask;
import de.nemeses.spring.bpmn.model.bpmn20.ServiceTask;
import de.nemeses.spring.bpmn.model.bpmn20.Task;
import de.nemeses.spring.bpmn.repository.NoSuchProcessInstanceException;
import de.nemeses.spring.bpmn.repository.ProcessInstance;
import de.nemeses.spring.bpmn.repository.ProcessInstanceRepository;

/**
 * Runs the work of a {@code serviceTask} (its {@link BpmnNodeImplementation}) or a {@code scriptTask} (its script,
 * through the {@link ScriptTaskInvoker}) for the {@link BpmnNodeExecutor} and stores the variables it changed; runs the
 * standard loops of service, script and plain tasks (through the {@link ActivityLoop}).
 */
@Component
class TaskInvoker {

    private final ProcessInstanceRepository instances;
    private final ActivityLoop loops;
    private final Map<String, BpmnNodeImplementation> implementations;
    private final ScriptTaskInvoker scripts;

    TaskInvoker(final ProcessInstanceRepository instances, final ActivityLoop loops,
            final Map<String, BpmnNodeImplementation> implementations, final ScriptTaskInvoker scripts) {
        this.instances = instances;
        this.loops = loops;
        this.implementations = implementations;
        this.scripts = scripts;
    }

    /**
     * Calls the {@link BpmnNodeImplementation} bean named by the extension attribute
     * {@link BpmnNodeExecutor#BEAN_ATTRIBUTE} (once, or per iteration of its standard loop through the
     * {@link ActivityLoop}, each iteration seeing the variables as changed by the previous ones) and stores the
     * variables changed through its {@link BpmnNodeContext} ({@link ProcessInstanceRepository#updateVariables}, once
     * after the last iteration and only if something changed).
     *
     * @param instance the (locked) process instance
     * @param task the service task
     * @param language the default expression language of the document (for a loop condition)
     * @return the variables including the changes of the task
     * @throws ProcessEngineException if the bean is missing or no {@link BpmnNodeImplementation}, the loop is not
     *             supported, the implementation failed or the variables cannot be stored
     */
    Map<String, Object> invoke(final ProcessInstance instance, final ServiceTask task, final String language)
            throws ProcessEngineException {
        final BpmnNodeImplementation implementation = this.implementation(task);
        return this.run(instance, task, language, implementation::execute);
    }

    /**
     * Runs the script of the task through the {@link ScriptTaskInvoker} (once, or per iteration of its standard loop
     * like {@link #invoke(ProcessInstance, ServiceTask, String)}) and stores the variables it changed
     * ({@link ProcessInstanceRepository#updateVariables}, once after the last iteration and only if something
     * changed).
     *
     * @param instance the (locked) process instance
     * @param task the script task
     * @param language the default expression language of the document (for a loop condition)
     * @return the variables including the changes of the script
     * @throws ProcessEngineException if the loop is not supported, the script fails or the variables cannot be stored
     */
    Map<String, Object> invoke(final ProcessInstance instance, final ScriptTask task, final String language)
            throws ProcessEngineException {
        return this.run(instance, task, language, context -> this.scripts.execute(task, context));
    }

    private Map<String, Object> run(final ProcessInstance instance, final Activity activity, final String language,
            final Body body) throws ProcessEngineException {
        final boolean[] changed = new boolean[1];
        final Map<String, Object> variables = this.loops.run(activity, language, instance.variables(),
                (loopCounter, current) -> {
                    final NodeContext context = new NodeContext(instance.id(), activity.getId(), current,
                            loopCounter);
                    body.execute(context);
                    changed[0] |= context.isChanged();
                    return context.changedVariables();
                });
        if (changed[0]) {
            try {
                this.instances.updateVariables(instance.id(), variables);
            } catch (final NoSuchProcessInstanceException e) {
                throw new ProcessEngineException("Cannot store the variables of process instance "
                        + instance.id() + ".", e);
            }
        }
        return variables;
    }

    /**
     * Runs the standard loop of a plain {@code task} (if any) through the {@link ActivityLoop}; a plain task has no
     * implementation, so its iterations only evaluate the loop condition.
     *
     * @param instance the (locked) process instance
     * @param task the plain task
     * @param language the default expression language of the document (for a loop condition)
     * @return the (unchanged) variables
     * @throws ProcessEngineException if the loop is not supported or its condition cannot be evaluated
     */
    Map<String, Object> loop(final ProcessInstance instance, final Task task, final String language)
            throws ProcessEngineException {
        return this.loops.run(task, language, instance.variables(), (_, current) -> current);
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

    /**
     * One execution of the work of a task.
     */
    @FunctionalInterface
    private interface Body {

        void execute(NodeContext context) throws ProcessEngineException;
    }
}
