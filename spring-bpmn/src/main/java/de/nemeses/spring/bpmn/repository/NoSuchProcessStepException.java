package de.nemeses.spring.bpmn.repository;

import java.util.UUID;

/**
 * Thrown if no {@link ProcessStep} exists for a requested id.
 */
public class NoSuchProcessStepException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * @param id the id for which no step was found
     */
    public NoSuchProcessStepException(final UUID id) {
        super("No process step found with id '" + id + "'");
    }
}
