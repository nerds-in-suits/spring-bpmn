package de.nemeses.spring.bpmn.rest;

import java.time.Instant;
import java.util.Date;

/**
 * Conversions of the {@link Date}s of the repository API for the JSON responses, which carry ISO-8601 instants.
 */
final class Dates {

    private Dates() {
        // static helpers only
    }

    /**
     * @param date a date, may be {@code null}
     * @return the instant of the date, {@code null} for {@code null}
     */
    static Instant instant(final Date date) {
        return date == null ? null : Instant.ofEpochMilli(date.getTime());
    }
}
