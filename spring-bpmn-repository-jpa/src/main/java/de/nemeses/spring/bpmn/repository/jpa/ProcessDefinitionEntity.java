package de.nemeses.spring.bpmn.repository.jpa;

import java.util.Date;
import java.util.UUID;

import de.nemeses.spring.bpmn.repository.ProcessDefinition;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 *
 */
@Entity
@Table(name = "spring_bpmn_process_definition")
public class ProcessDefinitionEntity implements ProcessDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "process_definition_name", nullable = false)
    private String processDefinitionName;

    @Column(name = "created_date", nullable = false)
    private Date created;

    @Lob
    @Column(name = "bpmn_xml", nullable = false, updatable = false)
    private String xml;

    ProcessDefinitionEntity() {
        // JPA
    }

    ProcessDefinitionEntity(final String name, final Date createdDate, final String bpmnXml) {
        this.processDefinitionName = name;
        this.created = createdDate;
        this.xml = bpmnXml;
    }

    @PrePersist
    void initCreated() {
        if (this.created == null) {
            this.created = new Date();
        }
    }

    @Override
    public String name() {
        return this.processDefinitionName;
    }

    @Override
    public Date createdDate() {
        return this.created;
    }

    @Override
    public String bpmnXml() {
        return this.xml;
    }
}
