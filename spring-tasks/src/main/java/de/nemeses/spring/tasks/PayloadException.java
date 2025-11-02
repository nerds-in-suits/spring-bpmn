package de.nemeses.spring.tasks;

/**
 *
 */
public class PayloadException extends RuntimeException {

    private static final long serialVersionUID = -6700568679489270603L;

    public PayloadException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public PayloadException(final String message) {
        super(message);
    }
}
