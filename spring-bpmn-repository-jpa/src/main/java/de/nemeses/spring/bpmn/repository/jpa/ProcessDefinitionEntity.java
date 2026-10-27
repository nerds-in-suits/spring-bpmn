package de.nemeses.spring.bpmn.repository.jpa;

import java.util.Date;

import de.nemeses.spring.bpmn.repository.ProcessDefinition;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

/**
 *
 */
@Entity
@Table(name = "spring_bpmn_process_definition")
public class ProcessDefinitionEntity implements ProcessDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String processName;

    @Column(name = "created_date", nullable = false)
    private Date created;

    @Lob
    @Column(name = "bpmn_xml", nullable = false)
    private String xml;

    ProcessDefinitionEntity() {
        // JPA
    }

    ProcessDefinitionEntity(final String name, final Date createdDate, final String bpmnXml) {
        this.processName = name;
        this.created = createdDate;
        this.xml = bpmnXml;
    }

    @Override
    public String name() {
        return this.processName;
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
