package de.nemeses.spring.bpmn;

import java.util.Map;
import java.util.UUID;

/**
 * The state a {@link BpmnNodeImplementation} sees while its node is executed. Read only for now; methods to change
 * process variables may be added later without changing {@link BpmnNodeImplementation}.
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
     * @return the current process variables, unmodifiable
     */
    Map<String, Object> variables();
}
