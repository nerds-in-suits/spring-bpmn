package de.nemeses.spring.tasks;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 *
 */
@SpringBootTest(classes = {TaskConfiguration.class, TaskServiceTest.PersistenceConfiguration.class},
    properties = "spring.tasks.pool-size=3")
public class TaskServiceTest {

    @Autowired
    private TaskService taskService;

    @Autowired
    private RecordingTaskPersistence persistence;

    @Autowired
    private TaskProperties properties;

    @BeforeEach
    public void resetPersistence() {
        this.persistence.reset();
    }

    @Test
    public void testSchedule() throws InterruptedException {
        this.taskService.trigger("testTaskImplementation", TestPayload.generate());
        final RecordingTaskPersistence.RecordedTask task = this.persistence.awaitFinished();
        Assertions.assertNotNull(task, "task executed");
        Assertions.assertEquals(1, this.persistence.persisted().size(), "one task persisted");
        Assertions.assertEquals("testTaskImplementation", task.beanName(), "bean name persisted");
    }

    @Test
    public void testWorkersStartedWithConfiguredPoolSize() throws InterruptedException {
        Assertions.assertTrue(this.taskService.isRunning(), "workers started with the context");
        Assertions.assertEquals(3, this.persistence.awaitWaiting(3), "one waiting worker per configured thread");
    }

    @Test
    public void testCompleteOnSuccess() throws InterruptedException {
        this.taskService.trigger("testTaskImplementation", TestPayload.generate());
        final RecordingTaskPersistence.RecordedTask task = this.persistence.awaitFinished();
        Assertions.assertNotNull(task, "task executed");
        Assertions.assertTrue(task.completed(), "task completed");
        Assertions.assertNull(task.error(), "task not failed");
    }

    @Test
    public void testFailOnException() throws InterruptedException {
        this.taskService.trigger("failingTaskImplementation", "boom");
        final RecordingTaskPersistence.RecordedTask task = this.persistence.awaitFinished();
        Assertions.assertNotNull(task, "task executed");
        Assertions.assertFalse(task.completed(), "task not completed");
        Assertions.assertInstanceOf(TaskException.class, task.error(), "failure reported");
        Assertions.assertEquals("boom", task.error().getMessage(), "original exception reported");
    }

    @Test
    public void testFailOnUnknownTask() throws InterruptedException {
        this.persistence.persist("unknownTask", null, null);
        final RecordingTaskPersistence.RecordedTask task = this.persistence.awaitFinished();
        Assertions.assertNotNull(task, "task taken");
        Assertions.assertInstanceOf(IllegalArgumentException.class, task.error(), "unknown bean reported");
    }

    @Test
    public void testStopInterruptsWorkers() throws InterruptedException {
        final RecordingTaskPersistence standalone = new RecordingTaskPersistence();
        final TaskService service = new TaskService(standalone,
            Map.of("testTaskImplementation", new TestTaskImplementation()), TaskServiceTest.properties(2));
        service.start();
        service.trigger("testTaskImplementation", TestPayload.generate());
        Assertions.assertTrue(standalone.awaitFinished().completed(), "task completed");
        Assertions.assertEquals(2, standalone.awaitWaiting(2), "both workers waiting again");
        service.stop();
        Assertions.assertFalse(service.isRunning(), "service stopped");
        Assertions.assertEquals(2, standalone.interrupted(), "every waiting worker interrupted");
        Assertions.assertEquals(0, standalone.waiting(), "no worker left waiting");
    }

    @Test
    public void testPropertiesBound() {
        Assertions.assertEquals(3, this.properties.poolSize(), "configured pool size");
        Assertions.assertEquals(Duration.ofSeconds(1), this.properties.errorBackoff(), "default error backoff");
        Assertions.assertEquals(Duration.ofSeconds(30), this.properties.shutdownTimeout(), "default shutdown timeout");
    }

    @Test
    public void testPoolSizeZeroStartsNoWorker() throws InterruptedException {
        final RecordingTaskPersistence standalone = new RecordingTaskPersistence();
        final TaskService service = new TaskService(standalone,
            Map.of("testTaskImplementation", new TestTaskImplementation()), TaskServiceTest.properties(0));
        service.start();
        Assertions.assertTrue(service.isRunning(), "service running without workers");
        service.trigger("testTaskImplementation", TestPayload.generate());
        Thread.sleep(200);
        Assertions.assertEquals(1, standalone.persisted().size(), "task scheduled");
        Assertions.assertEquals(0, standalone.waiting(), "no worker waiting for tasks");
        Assertions.assertEquals(0, standalone.finishedCount(), "no task executed");
        service.stop();
        Assertions.assertFalse(service.isRunning(), "service stopped");
    }

    @Test
    public void testNegativePoolSizeRejected() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> TaskServiceTest.properties(-1),
            "negative pool size rejected");
    }

    private static TaskProperties properties(final int poolSize) {
        return new TaskProperties(poolSize, Duration.ofMillis(100), Duration.ofSeconds(5));
    }

    /**
     * Provides the test persistence and a failing task.
     */
    @Configuration
    static class PersistenceConfiguration {

        @Bean
        RecordingTaskPersistence taskPersistence() {
            return new RecordingTaskPersistence();
        }

        @Bean
        TaskImplementation<String> failingTaskImplementation() {
            return new FailingTaskImplementation();
        }
    }

    /**
     * Task that always fails with its payload as message.
     */
    static class FailingTaskImplementation implements TaskImplementation<String> {

        @Override
        public void execute(final String payload) throws TaskException {
            throw new TaskException(payload);
        }

        @Override
        public String serializePayload(final String payload) {
            return payload;
        }

        @Override
        public String deserializePayload(final String serializedPayload) {
            return serializedPayload;
        }
    }
}
