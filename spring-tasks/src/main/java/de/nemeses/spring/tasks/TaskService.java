package de.nemeses.spring.tasks;

import java.util.Date;
import java.util.Map;

import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 *
 */
@Component
public class TaskService {

    private final TaskPersistence persistence;
    private final Map<String, TaskImplementation<?>> tasks;

    TaskService(final TaskPersistence persistence, final Map<String, TaskImplementation<?>> tasks) {
        this.persistence = persistence;
        LoggerFactory.getLogger(this.getClass()).info("Using task persistence {}.", persistence);
        this.tasks = tasks;
        LoggerFactory.getLogger(this.getClass()).debug("Registered tasks: {}", this.tasks);
    }

    public void trigger(final String beanName, final Object payload) {
        this.schedule(beanName, payload, null);
    }

    public void schedule(final String beanName, final Object payload, final Date earliestExecution) {
        final String serializedPayload = this.taskImplementation(beanName).serializePayloadObj(payload);
        this.persistence.persist(new TaskPersistence.Data(beanName, serializedPayload, earliestExecution));
    }

    private TaskImplementation<?> taskImplementation(final String beanName) {
        final TaskImplementation<?> ret = this.tasks.get(beanName);
        if(ret == null) {
            throw new IllegalArgumentException("No task implementation registered for bean name '"+beanName+"'.");
        }
        return ret;
    }
}
