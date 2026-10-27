package de.nemeses.spring.bpmn.repository.jpa;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * One variable of a {@link ProcessInstanceEntity}; the value is stored as JSON. The primary key is the owning instance
 * plus the variable name ({@link ProcessVariableEntity.ProcessVariableId}).
 */
@Entity
@IdClass(ProcessVariableEntity.ProcessVariableId.class)
@Table(name = "spring_bpmn_process_variable")
public class ProcessVariableEntity {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    @Id
    @ManyToOne(optional = false)
    @JoinColumn(name = "process_instance_id", nullable = false, updatable = false)
    private ProcessInstanceEntity instance;

    @Id
    @Column(name = "variable_name", nullable = false, updatable = false)
    private String name;

    @Lob
    @Column(name = "variable_value", nullable = false)
    private String json;

    ProcessVariableEntity() {
        // JPA
    }

    ProcessVariableEntity(final ProcessInstanceEntity instance, final String name, final Object value) {
        this.instance = instance;
        this.name = name;
        this.json = ProcessVariableEntity.toJson(value);
    }

    /**
     * @return the deserialized value
     */
    Object value() {
        try {
            return ProcessVariableEntity.MAPPER.readValue(this.json, Object.class);
        } catch (final JacksonException e) {
            throw new IllegalArgumentException("Cannot deserialize process variable '" + this.name + "'.", e);
        }
    }

    /**
     * Sets a new value; the row is only updated if its JSON changes.
     *
     * @param value the new value
     */
    void value(final Object value) {
        final String newJson = ProcessVariableEntity.toJson(value);
        if (!Objects.equals(this.json, newJson)) {
            this.json = newJson;
        }
    }

    private static String toJson(final Object value) {
        try {
            return ProcessVariableEntity.MAPPER.writeValueAsString(value);
        } catch (final JacksonException e) {
            throw new IllegalArgumentException("Cannot serialize process variable.", e);
        }
    }

    /**
     * Composite primary key of a {@link ProcessVariableEntity}: the id of the owning process instance and the variable
     * name.
     */
    public static class ProcessVariableId implements Serializable {

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
}
