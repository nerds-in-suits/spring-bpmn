package de.nemeses.spring.bpmn;

/**
 * Code bound to a BPMN {@code serviceTask}. Implementations are Spring beans; a service task refers to one by its
 * bean name in the extension attribute {@code bean} of the namespace {@value BpmnNodeExecutor#NAMESPACE}, e.g.
 * {@code <serviceTask id="check" xmlns:spring="https://nemeses.de/spring-bpmn" spring:bean="checkOrder"/>}.
 */
public interface BpmnNodeImplementation {

    /**
     * Executes the service task. The outgoing sequence flows are only taken if this method returns normally; an
     * exception rolls back the node execution and fails the {@link BpmnNodeTask}, which the task backend redelivers.
     *
     * @param context the process instance and node being executed
     * @throws ProcessEngineException if the task failed
     */
    void execute(BpmnNodeContext context) throws ProcessEngineException;
}
