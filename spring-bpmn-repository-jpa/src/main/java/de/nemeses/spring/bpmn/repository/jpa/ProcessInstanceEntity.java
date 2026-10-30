package de.nemeses.spring.bpmn.repository.jpa;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import de.nemeses.spring.bpmn.repository.ProcessDefinition;
import de.nemeses.spring.bpmn.repository.ProcessInstance;
import de.nemeses.spring.bpmn.repository.ProcessStep;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKey;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/**
 *
 */
@Entity
@Table(name = "spring_bpmn_process_instance",
        indexes = @Index(name = "ix_spring_bpmn_process_instance_parent", columnList = "parent_step_id"))
public class ProcessInstanceEntity implements ProcessInstance {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID uuid;

    @ManyToOne(optional = false)
    @JoinColumn(name = "process_definition_id", nullable = false, updatable = false)
    private ProcessDefinitionEntity definition;

    /**
     * One row per variable (table {@code spring_bpmn_process_variable}), keyed by variable name. Not final, because
     * Hibernate replaces the map with its persistent collection.
     */
    @SuppressWarnings("PMD.ImmutableField")
    @OneToMany(mappedBy = "instance", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @MapKey(name = "name")
    private Map<String, ProcessVariableEntity> vars = new LinkedHashMap<>();

    @Column(name = "start_date", nullable = false, updatable = false)
    private Date start;

    /**
     * The waiting call activity step of the parent instance ({@code null} for a top-level instance). Its instance is
     * loaded lazily, so {@link #parentInstance()} needs an open persistence context.
     */
    @ManyToOne
    @JoinColumn(name = "parent_step_id", updatable = false)
    private ProcessStepEntity parent;

    /** The same column as {@link #parent}, for queries without join. */
    @Column(name = "parent_step_id", insertable = false, updatable = false)
    private UUID parentStepId;

    @Column(name = "end_date")
    private Date end;

    ProcessInstanceEntity() {
        // JPA
    }

    ProcessInstanceEntity(final UUID id, final ProcessDefinitionEntity definition, final Map<String, Object> variables,
            final Date startDate, final ProcessStepEntity parentStep) {
        this.uuid = id;
        this.definition = definition;
        this.start = startDate;
        this.parent = parentStep;
        this.parentStepId = parentStep == null ? null : parentStep.id();
        this.replaceVariables(variables);
    }

    @Override
    @SuppressWarnings("PMD.ShortMethodName")
    public UUID id() {
        return this.uuid;
    }

    @Override
    public ProcessDefinition processDefinition() {
        return this.definition;
    }

    @Override
    public Map<String, Object> variables() {
        final Map<String, Object> values = new LinkedHashMap<>();
        this.vars.forEach((name, variable) -> values.put(name, variable.value()));
        return Collections.unmodifiableMap(values);
    }

    @Override
    public Date startDate() {
        return this.start;
    }

    @Override
    public Date endDate() {
        return this.end;
    }

    @Override
    public ProcessStep parentStep() {
        return this.parent;
    }

    @Override
    public ProcessInstance parentInstance() {
        return this.parent == null ? null : this.parent.processInstance();
    }

    void endDate(final Date endDate) {
        this.end = endDate;
    }

    /**
     * Replaces all variables: rows of variables no longer present are deleted, rows of remaining variables are updated
     * in place (only if their JSON value changed) and new variables get new rows. Updating in place also avoids a
     * delete and insert of the same name in one flush, which would violate the primary key (instance and name).
     *
     * @param variables the new variables
     */
    void variables(final Map<String, Object> variables) {
        this.replaceVariables(variables);
    }

    private void replaceVariables(final Map<String, Object> variables) {
        for (final String name : new ArrayList<>(this.vars.keySet())) {
            if (!variables.containsKey(name)) {
                this.vars.remove(name);
            }
        }
        variables.forEach((name, value) -> {
            final ProcessVariableEntity variable = this.vars.get(name);
            if (variable == null) {
                this.vars.put(name, new ProcessVariableEntity(this, name, value));
            } else {
                variable.value(value);
            }
        });
    }
}
