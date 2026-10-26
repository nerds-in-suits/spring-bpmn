package de.nemeses.spring.tasks;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 *
 */
@SpringBootTest(classes = {TaskConfiguration.class, TaskServiceTest.PersistenceConfiguration.class})
public class TaskServiceTest {

    @Autowired
    private TaskService taskService;

    @Autowired
    private RecordingTaskPersistence persistence;

    @Test
    public void testSchedule() {
        this.taskService.trigger("testTaskImplementation", TestPayload.generate());
        Assertions.assertEquals(1, this.persistence.persisted().size());
        Assertions.assertEquals("testTaskImplementation", this.persistence.persisted().get(0).beanName());
    }

    /**
     * Stands in for a real persistence backend, which needs JPA or JMS on the classpath.
     */
    @Configuration
    static class PersistenceConfiguration {

        @Bean
        RecordingTaskPersistence taskPersistence() {
            return new RecordingTaskPersistence();
        }
    }

    record Persisted(String beanName, String payload, Date earliestExecution) {
    }

    static class RecordingTaskPersistence implements TaskPersistence {
        private final List<Persisted> persisted = new ArrayList<>();

        @Override
        public void persist(final String beanName, final String payload, final Date earliestExecution) {
            this.persisted.add(new Persisted(beanName, payload, earliestExecution));
        }

        @Override
        public TaskInfo next() {
            throw new UnsupportedOperationException("Not needed by this test.");
        }

        List<Persisted> persisted() {
            return this.persisted;
        }
    }
}
