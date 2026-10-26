package de.nemeses.spring.tasks;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
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
        private final List<TaskInfo> persisted = new ArrayList<>();

        @Override
        public void persist(final String beanName, final String payload, final Date earliestExecution) {
            this.persisted.add(new TaskInfo() {

                @Override
                public String beanName() {
                    return beanName;
                }

                @Override
                public String payload() {
                    return payload;
                }

                @Override
                public Date earliestExecution() {
                    return earliestExecution;
                }

                @Override
                public void complete() {
                    RecordingTaskPersistence.this.persisted.remove(this);
                }

                @Override
                public void fail(final Exception error) {
                    LoggerFactory.getLogger(this.getClass()).error("Failed to execute {} task.",
                        this.beanName(), error);
                }

            });
        }

        List<TaskInfo> persisted() {
            return this.persisted;
        }

        @Override
        public TaskInfo next() throws InterruptedException {
            // single threaded only
            final Date now = new Date();
            for(final TaskInfo info:this.persisted) {
                if(info.earliestExecution() == null || info.earliestExecution().after(now)) {
                    return info;
                }
            }
            Thread.sleep(3000);
            return this.next();
        }
    }
}
