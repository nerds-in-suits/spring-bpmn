package de.nemeses.spring.tasks.jpa;

import java.util.Date;

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

    TaskEntity() {
        // JPA
    }

    TaskEntity(final String beanName, final String payload, final Date execution) {
        this.beanName = beanName;
        this.payload = payload;
        this.execution = execution;
    }
}
