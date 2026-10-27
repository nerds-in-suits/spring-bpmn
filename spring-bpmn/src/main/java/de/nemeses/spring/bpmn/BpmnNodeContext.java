package de.nemeses.spring.bpmn;

import java.util.Map;
import java.util.UUID;

/**
 * The state a {@link BpmnNodeImplementation} sees while its node is executed. Changes to the process variables are
 * stored when the node completes successfully, in the same transaction as the node execution and before the following
 * nodes are scheduled; if the node fails, they are discarded.
 */
public interface BpmnNodeContext {

    /**
     * @return the id of the process instance the node is executed for
     */
    UUID processInstanceId();

    /**
     * @return the id of the executed BPMN flow node
     */
    String nodeId();

    /**
     * @return the current process variables including the changes made through this context, unmodifiable
     */
    Map<String, Object> variables();

    /**
     * Sets a process variable. The value must be serializable by the {@code ProcessInstanceRepository} in use (the JPA
     * repository stores variables as JSON).
     *
     * @param name the name of the variable
     * @param value the new value; use {@link #removeVariable(String)} to remove a variable
     * @throws NullPointerException if name or value is {@code null}
     */
    void setVariable(String name, Object value);

    /**
     * Removes a process variable; does nothing if it does not exist.
     *
     * @param name the name of the variable
     */
    void removeVariable(String name);
}
