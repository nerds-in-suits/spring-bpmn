package de.nemeses.spring.bpmn.repository;

import java.util.UUID;

/**
 * Thrown if no {@link ProcessInstance} exists for a requested id.
 */
public class NoSuchProcessInstanceException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * @param id the id for which no instance was found
     */
    public NoSuchProcessInstanceException(final UUID id) {
        super("No process instance found with id '" + id + "'");
    }
}
