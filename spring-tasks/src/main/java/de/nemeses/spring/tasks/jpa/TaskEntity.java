package de.nemeses.spring.tasks.jpa;

import jakarta.persistence.Lob;

/**
 *
 */
public class TaskEntity {
    private String beanName;
    @Lob
    private String payload;
    private Date execution;


}
