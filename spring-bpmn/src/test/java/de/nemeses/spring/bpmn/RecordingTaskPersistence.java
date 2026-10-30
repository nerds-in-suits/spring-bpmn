package de.nemeses.spring.bpmn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import de.nemeses.spring.tasks.TaskPersistence;

/**
 * Records persisted tasks instead of storing them.
 */
public class RecordingTaskPersistence implements TaskPersistence {

    private final List<Recorded> persisted = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void persist(final String beanName, final String payload, final Date earliestExecution) {
        this.persisted.add(new Recorded(beanName, payload));
    }

    @Override
    public TaskInfo next() throws InterruptedException {
        // Recorded tasks are never delivered: block the TaskService worker until it is interrupted on shutdown.
        new CountDownLatch(1).await();
        throw new InterruptedException("Tasks are only recorded.");
    }

    public List<Recorded> persisted() {
        return this.persisted;
    }

    /**
     * A recorded task.
     *
     * @param beanName the task bean name
     * @param payload the serialized payload
     */
    public record Recorded(String beanName, String payload) {
    }
}
