package de.nemeses.spring.bpmn;

public class BpmnParseException extends Exception {

    private static final long serialVersionUID = 6370155920184774239L;

    public BpmnParseException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
