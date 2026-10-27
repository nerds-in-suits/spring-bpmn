package de.nemeses.spring.bpmn.repository.jpa;

import java.util.Date;
import java.util.UUID;

import de.nemeses.spring.bpmn.repository.ProcessStep;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 *
 */
@Entity
@Table(name = "spring_bpmn_process_step", indexes = {
    @Index(name = "ix_spring_bpmn_process_step_pending", columnList = "process_instance_id, end_date"),
    @Index(name = "ix_spring_bpmn_process_step_node", columnList = "process_instance_id, node_id, end_date") })
public class ProcessStepEntity implements ProcessStep {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID uuid;

    /** Mapped for the foreign key and {@link ProcessInstanceEntity#parentInstance()}; never loaded by the engine. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "process_instance_id", nullable = false, updatable = false)
    private ProcessInstanceEntity instance;

    /** The same column as {@link #instance}, readable without loading the instance. */
    @Column(name = "process_instance_id", nullable = false, insertable = false, updatable = false)
    private UUID instanceId;

    @Column(name = "node_id", nullable = false, updatable = false)
    private String node;

    @Column(name = "incoming_flow_id", updatable = false)
    private String incomingFlow;

    @Column(name = "start_date", nullable = false, updatable = false)
    private Date start;

    @Column(name = "end_date")
    private Date end;

    ProcessStepEntity() {
        // JPA
    }

    ProcessStepEntity(final UUID id, final ProcessInstanceEntity instance, final String nodeId,
            final String incomingFlowId, final Date startDate) {
        this.uuid = id;
        this.instance = instance;
        this.instanceId = instance.id();
        this.node = nodeId;
        this.incomingFlow = incomingFlowId;
        this.start = startDate;
    }

    @Override
    @SuppressWarnings("PMD.ShortMethodName")
    public UUID id() {
        return this.uuid;
    }

    @Override
    public UUID processInstanceId() {
        return this.instanceId;
    }

    @Override
    public String nodeId() {
        return this.node;
    }

    @Override
    public String incomingFlowId() {
        return this.incomingFlow;
    }

    @Override
    public Date startDate() {
        return this.start;
    }

    @Override
    public Date endDate() {
        return this.end;
    }

    /**
     * @return the owning instance, loaded lazily
     */
    ProcessInstanceEntity processInstance() {
        return this.instance;
    }

    void endDate(final Date endDate) {
        this.end = endDate;
    }
}
