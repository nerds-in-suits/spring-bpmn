package de.nemeses.spring.bpmn;

/**
 * Thrown when a BPMN document cannot be parsed.
 */
public class BpmnParseException extends Exception {

    private static final long serialVersionUID = 6370155920184774239L;

    public BpmnParseException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
