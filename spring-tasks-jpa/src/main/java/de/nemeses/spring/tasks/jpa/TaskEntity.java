package de.nemeses.spring.tasks.jpa;

import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

/**
 *
 */
@Entity
@Table(name = "spring_tasks")
public class TaskEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "beanname")
    private String beanName;

    @Lob
    private String payload;

    @Column(name = "execution")
    private Date execution;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private Status status = Status.PENDING;

    @Lob
    @Column(name = "error")
    private String error;

    /**
     * Lifecycle of a persisted task.
     */
    enum Status {
        PENDING, RUNNING, COMPLETED, FAILED
    }

    TaskEntity() {
        // JPA
    }

    TaskEntity(final String beanName, final String payload, final Date execution) {
        this.beanName = beanName;
        this.payload = payload;
        this.execution = execution;
    }

    Long getId() {
        return this.id;
    }

    String getBeanName() {
        return this.beanName;
    }

    String getPayload() {
        return this.payload;
    }

    Date getExecution() {
        return this.execution;
    }

    void setStatus(final Status newStatus) {
        this.status = newStatus;
    }

    void setError(final String newError) {
        this.error = newError;
    }
}
