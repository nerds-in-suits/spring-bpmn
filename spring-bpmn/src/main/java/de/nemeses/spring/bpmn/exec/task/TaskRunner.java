package de.nemeses.spring.bpmn.exec.task;

import java.util.Map;

import org.springframework.stereotype.Component;

import de.nemeses.spring.bpmn.exec.NodeExecution;
import de.nemeses.spring.bpmn.exec.ProcessEngineException;
import de.nemeses.spring.bpmn.model.bpmn20.Activity;
import de.nemeses.spring.bpmn.repository.NoSuchProcessInstanceException;
import de.nemeses.spring.bpmn.repository.ProcessInstance;
import de.nemeses.spring.bpmn.repository.ProcessInstanceRepository;

/**
 * Runs the work of a task for the task invokers: once, or per iteration of its standard loop (through the
 * {@link ActivityLoop}), each iteration seeing the variables as changed by the previous ones, and stores the variables
 * changed through its {@link NodeContext} ({@link ProcessInstanceRepository#updateVariables}, once after the last
 * iteration and only if something changed).
 */
@Component
class TaskRunner {

    private final ProcessInstanceRepository instances;
    private final ActivityLoop loops;

    TaskRunner(final ProcessInstanceRepository instances, final ActivityLoop loops) {
        this.instances = instances;
        this.loops = loops;
    }

    /**
     * @param activity the task
     * @param execution the step being executed (for its instance and the expression language of a loop condition)
     * @param body one execution of the work of the task
     * @return the variables including the changes of the task
     * @throws ProcessEngineException if the loop is not supported, the work failed or the variables cannot be stored
     */
    Map<String, Object> run(final Activity activity, final NodeExecution execution, final Body body)
            throws ProcessEngineException {
        final ProcessInstance instance = execution.instance();
        final boolean[] changed = new boolean[1];
        final Map<String, Object> variables = this.loops.run(activity, execution.language(), instance.variables(),
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
     * Runs the standard loop of a task without work (if any); its iterations only evaluate the loop condition.
     *
     * @param activity the task
     * @param execution the step being executed
     * @return the (unchanged) variables
     * @throws ProcessEngineException if the loop is not supported or its condition cannot be evaluated
     */
    Map<String, Object> loop(final Activity activity, final NodeExecution execution) throws ProcessEngineException {
        return this.loops.run(activity, execution.language(), execution.instance().variables(),
                (_, current) -> current);
    }

    /**
     * One execution of the work of a task.
     */
    @FunctionalInterface
    interface Body {

        /**
         * @param context the context of the node, receives the changed variables
         * @throws ProcessEngineException if the work failed
         */
        void execute(NodeContext context) throws ProcessEngineException;
    }
}
