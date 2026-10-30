package de.nemeses.spring.bpmn.exec;

import java.util.Map;
import java.util.UUID;

/**
 * The state a {@link BpmnNodeImplementation} sees while its node is executed. Changes to the process variables are
 * stored when the node completes successfully, in the same transaction as the node execution and before the following
 * nodes are scheduled; if the node fails, they are discarded.
 * <p>
 * The context starts from the variables read after the process instance was locked. Because of that lock, the steps
 * of parallel paths of one instance read and write the variables one after the other; the stored variables are
 * replaced as a whole, so no update is lost.
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
     * @return the number of iterations of the node's standard loop ({@code standardLoopCharacteristics}) completed
     *         before this execution; {@code 0} for the first iteration and for nodes without loop
     */
    int loopCounter();

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
