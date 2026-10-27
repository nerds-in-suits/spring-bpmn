package de.nemeses.spring.bpmn.repository;

/**
 * Thrown if no {@link ProcessDefinition} exists for a requested name.
 */
public class NoSuchProcessDefinitionException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * @param name the name for which no definition was found
     */
    public NoSuchProcessDefinitionException(final String name) {
        super("No process definition found with name '" + name + "'");
    }
}
