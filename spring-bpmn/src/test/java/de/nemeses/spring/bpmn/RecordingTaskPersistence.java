package de.nemeses.spring.bpmn;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import de.nemeses.spring.tasks.TaskPersistence;

/**
 * Records persisted tasks instead of storing them.
 */
class RecordingTaskPersistence implements TaskPersistence {

    private final List<Recorded> persisted = new ArrayList<>();

    @Override
    public void persist(final String beanName, final String payload, final Date earliestExecution) {
        this.persisted.add(new Recorded(beanName, payload));
    }

    @Override
    public TaskInfo next() {
        throw new UnsupportedOperationException("Tasks are only recorded.");
    }

    List<Recorded> persisted() {
        return this.persisted;
    }

    /**
     * A recorded task.
     *
     * @param beanName the task bean name
     * @param payload the serialized payload
     */
    record Recorded(String beanName, String payload) {
    }
}
