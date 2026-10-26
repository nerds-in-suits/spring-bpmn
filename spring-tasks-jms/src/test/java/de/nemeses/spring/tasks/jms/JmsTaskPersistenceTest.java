package de.nemeses.spring.tasks.jms;

import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.nemeses.spring.tasks.TaskPersistence.TaskInfo;

/**
 *
 */
class JmsTaskPersistenceTest {

    private static EmbeddedActiveMQ broker;

    private JmsTaskPersistence persistence;

    @BeforeAll
    static void startBroker() throws Exception {
        final ConfigurationImpl configuration = new ConfigurationImpl();
        configuration.setPersistenceEnabled(false);
        configuration.setSecurityEnabled(false);
        configuration.addAcceptorConfiguration("invm", "vm://0");
        broker = new EmbeddedActiveMQ();
        broker.setConfiguration(configuration);
        broker.start();
    }

    @AfterAll
    static void stopBroker() throws Exception {
        broker.stop();
    }

    @BeforeEach
    void setUp() {
        this.persistence = new JmsTaskPersistence(new ActiveMQConnectionFactory("vm://0"),
            "queue-" + System.nanoTime());
    }

    @AfterEach
    void tearDown() throws Exception {
        this.persistence.close();
    }

    @Test
    void testPersistAndNext() throws Exception {
        this.persistence.persist("bean", "{\"a\":1}", null);
        final TaskInfo info = this.persistence.next();
        Assertions.assertEquals("bean", info.beanName());
        Assertions.assertEquals("{\"a\":1}", info.payload());
        Assertions.assertNull(info.earliestExecution());
        info.complete();
    }

    @Test
    void testEarliestExecutionDelaysDelivery() throws Exception {
        final Date earliest = new Date(System.currentTimeMillis() + 1500);
        this.persistence.persist("bean", "p", earliest);
        final TaskInfo info = this.persistence.next();
        Assertions.assertTrue(System.currentTimeMillis() >= earliest.getTime() - 50, "delivered too early");
        Assertions.assertEquals(earliest, info.earliestExecution());
        info.complete();
    }

    @Test
    void testFailRedelivers() throws Exception {
        this.persistence.persist("bean", "p", null);
        this.persistence.next().fail(new IllegalStateException("boom"));
        final TaskInfo again = this.persistence.next();
        Assertions.assertEquals("p", again.payload());
        again.complete();
    }

    @Test
    void testCompleteRemovesTask() throws Exception {
        this.persistence.persist("first", "1", null);
        this.persistence.persist("second", "2", null);
        this.persistence.next().complete();
        final TaskInfo second = this.persistence.next();
        Assertions.assertEquals("second", second.beanName());
        second.complete();
    }

    @Test
    void testNextBlocksUntilInterrupted() throws Exception {
        this.persistence.persist("warmup", "", null);
        this.persistence.next().complete();
        final CompletableFuture<Boolean> interrupted = new CompletableFuture<>();
        final Thread thread = new Thread(() -> {
            try {
                this.persistence.next();
                interrupted.complete(false);
            } catch (final InterruptedException e) {
                interrupted.complete(true);
            } catch (final IllegalStateException e) {
                interrupted.completeExceptionally(e);
            }
        });
        thread.start();
        Thread.sleep(500);
        thread.interrupt();
        Assertions.assertTrue(interrupted.get(10, TimeUnit.SECONDS));
    }
}
