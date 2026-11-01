package de.nemeses.spring.bpmn.exec;

/**
 * Thrown if the process engine cannot perform an operation, e.g. start a process.
 */
public class ProcessEngineException extends Exception {

    private static final long serialVersionUID = -2717946227305941813L;

    /**
     * @param message the detail message
     */
    public ProcessEngineException(final String message) {
        super(message);
    }

    /**
     * @param message the detail message
     * @param cause the cause
     */
    public ProcessEngineException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
