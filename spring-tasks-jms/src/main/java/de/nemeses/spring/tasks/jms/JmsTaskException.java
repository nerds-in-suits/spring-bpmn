package de.nemeses.spring.tasks.jms;

/**
 * Thrown when a task cannot be sent to or received from the JMS queue.
 */
public class JmsTaskException extends RuntimeException {

    private static final long serialVersionUID = 4172950264183905637L;

    JmsTaskException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
