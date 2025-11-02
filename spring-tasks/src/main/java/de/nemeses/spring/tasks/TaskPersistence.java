package de.nemeses.spring.tasks;

import java.util.Date;

/**
 *
 */
public interface TaskPersistence {

    void persist(Data data);

    record Data(String beanName, String payload, Date earliestExecution) {}
}
