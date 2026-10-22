package de.nemeses.spring.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

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
        assertEquals(1, this.persistence.persisted().size());
        assertEquals("testTaskImplementation", this.persistence.persisted().get(0).beanName());
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

    static class RecordingTaskPersistence implements TaskPersistence {
        private final List<Data> persisted = new ArrayList<>();

        @Override
        public void persist(final Data data) {
            this.persisted.add(data);
        }

        List<Data> persisted() {
            return this.persisted;
        }
    }
}
