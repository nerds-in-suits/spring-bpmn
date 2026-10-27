package de.nemeses.spring.bpmn.repository.jpa;

import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import de.nemeses.spring.bpmn.repository.ProcessDefinition;
import de.nemeses.spring.bpmn.repository.ProcessInstance;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 *
 */
@Entity
@Table(name = "spring_bpmn_process_instance")
public class ProcessInstanceEntity implements ProcessInstance {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID uuid;

    @ManyToOne(optional = false)
    @JoinColumn(name = "process_definition_id", nullable = false, updatable = false)
    private ProcessDefinitionEntity definition;

    @Lob
    @Convert(converter = VariablesConverter.class)
    @Column(name = "variables", nullable = false)
    private Map<String, Object> vars;

    @Column(name = "start_date", nullable = false, updatable = false)
    private Date start;

    @Column(name = "end_date")
    private Date end;

    ProcessInstanceEntity() {
        // JPA
    }

    ProcessInstanceEntity(final UUID id, final ProcessDefinitionEntity definition, final Map<String, Object> variables,
            final Date startDate) {
        this.uuid = id;
        this.definition = definition;
        this.vars = new LinkedHashMap<>(variables);
        this.start = startDate;
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
        return Collections.unmodifiableMap(this.vars);
    }

    @Override
    public Date startDate() {
        return this.start;
    }

    @Override
    public Date endDate() {
        return this.end;
    }

    void endDate(final Date endDate) {
        this.end = endDate;
    }

    /**
     * Replaces the variables with a copy, so that the converted attribute is detected as dirty.
     *
     * @param variables the new variables
     */
    void variables(final Map<String, Object> variables) {
        this.vars = new LinkedHashMap<>(variables);
    }
}
