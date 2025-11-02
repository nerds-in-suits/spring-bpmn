package de.nemeses.spring.tasks;

/**
 *
 */
public class TaskException extends Exception {

    private static final long serialVersionUID = 5789425314823125568L;

    public TaskException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public TaskException(final String message) {
        super(message);
    }
}
