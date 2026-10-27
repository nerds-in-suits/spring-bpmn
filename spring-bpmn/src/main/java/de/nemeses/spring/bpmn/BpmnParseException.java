package de.nemeses.spring.bpmn;

/**
 * Thrown when a BPMN document cannot be parsed.
 */
public class BpmnParseException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * @param message the message
     * @param cause the cause
     */
    public BpmnParseException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
