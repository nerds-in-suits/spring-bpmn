package de.nemeses.spring.bpmn.rest;

import java.util.Date;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * State of a process instance or step: running (pending) while it has no end date, else finished. Its
 * {@link #value()} is used in responses and in the {@code state} query parameter.
 */
enum State {

    /** No end date. */
    RUNNING("running"),

    /** Has an end date. */
    FINISHED("finished");

    private final String text;

    State(final String value) {
        this.text = value;
    }

    /**
     * @return the value in responses and query parameters
     */
    String value() {
        return this.text;
    }

    /**
     * @param endDate the end date of an instance or step
     * @return its state
     */
    static State from(final Date endDate) {
        return endDate == null ? RUNNING : FINISHED;
    }

    /**
     * @param value a {@code state} query parameter, may be {@code null}
     * @return the state, {@code null} for {@code null} (any state)
     * @throws ResponseStatusException with {@code 400 Bad Request} if the value is unknown
     */
    static State parse(final String value) {
        if (value == null) {
            return null;
        }
        for (final State state : State.values()) {
            if (state.text.equals(value)) {
                return state;
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Unknown state '" + value + "', expected 'running' or 'finished'.");
    }

    /**
     * @param value a {@code direction} query parameter, may be {@code null}
     * @return {@code true} for {@code asc} or {@code null}, {@code false} for {@code desc}
     * @throws ResponseStatusException with {@code 400 Bad Request} for other values
     */
    static boolean ascending(final String value) {
        if (value == null || "asc".equals(value)) {
            return true;
        }
        if ("desc".equals(value)) {
            return false;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Unknown direction '" + value + "', expected 'asc' or 'desc'.");
    }
}
