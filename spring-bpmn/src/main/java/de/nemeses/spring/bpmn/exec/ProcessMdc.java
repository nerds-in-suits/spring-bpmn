package de.nemeses.spring.bpmn.exec;

import java.util.Map;
import java.util.UUID;

import de.nemeses.spring.bpmn.repository.ProcessStep;
import de.nemeses.spring.tasks.TaskMdc;

/**
 * Keys of the SLF4J {@code MDC} entries the engine sets while it works on a process instance, in addition to the
 * {@link TaskMdc#TASK_ID} of the {@code bpmnNodeTask} task.
 * <p>
 * {@link BpmnNodeExecutor} sets {@link #PROCESS_INSTANCE_ID}, {@link #STEP_ID} and {@link #NODE_ID} while it executes a
 * step, whether from its task or from {@link ProcessEngineService#completeUserTask}; {@link InvokerRegistry} sets
 * {@link #PROCESS_INSTANCE_ID} while it triggers a node, such as the start events of a new instance. The previous
 * values are restored afterwards (see {@link TaskMdc.Scope}), so a call activity starting a child instance within a step leaves
 * the parent's values in place. The key names are fixed constants, not configuration.
 */
public final class ProcessMdc {

    /** MDC key of the id of the process instance. */
    public static final String PROCESS_INSTANCE_ID = "processInstanceId";

    /** MDC key of the id of the executed process step. */
    public static final String STEP_ID = "stepId";

    /** MDC key of the BPMN id of the flow node of the executed step. */
    public static final String NODE_ID = "nodeId";

    private ProcessMdc() {
    }

    /**
     * Sets the process instance id until the returned scope is closed.
     */
    static TaskMdc.Scope put(final UUID processInstanceId) {
        return TaskMdc.put(ProcessMdc.PROCESS_INSTANCE_ID, processInstanceId.toString());
    }

    /**
     * Sets the process instance id, step id and node id of the step until the returned scope is closed.
     */
    static TaskMdc.Scope put(final ProcessStep step) {
        return TaskMdc.put(Map.of(ProcessMdc.PROCESS_INSTANCE_ID, step.processInstanceId().toString(),
                ProcessMdc.STEP_ID, step.id().toString(), ProcessMdc.NODE_ID, step.nodeId()));
    }
}
