package de.nemeses.spring.bpmn.repository.jpa;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite primary key of a {@link ProcessVariableEntity}: the id of the owning process instance and the variable
 * name.
 */
public class ProcessVariableId implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private UUID instance;

    private String name;

    /**
     * For JPA.
     */
    public ProcessVariableId() {
        // JPA
    }

    ProcessVariableId(final UUID instance, final String name) {
        this.instance = instance;
        this.name = name;
    }

    @Override
    public boolean equals(final Object obj) {
        return obj instanceof final ProcessVariableId other && Objects.equals(this.instance, other.instance)
                && Objects.equals(this.name, other.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.instance, this.name);
    }
}
