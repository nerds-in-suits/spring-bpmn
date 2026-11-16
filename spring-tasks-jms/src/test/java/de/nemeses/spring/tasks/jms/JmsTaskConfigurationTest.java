package de.nemeses.spring.tasks.jms;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import de.nemeses.spring.tasks.TaskConfiguration;
import de.nemeses.spring.tasks.TaskPersistence;
import de.nemeses.spring.tasks.TaskService;
import jakarta.jms.ConnectionFactory;

/**
 * Starts {@link TaskConfiguration} together with {@link JmsTaskPersistenceAutoConfiguration}: the
 * {@link TaskService} uses the {@link JmsTaskPersistence} registered by the auto-configuration.
 */
class JmsTaskConfigurationTest {

    @Test
    void testJmsTaskPersistenceIsUsed() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JmsTaskPersistenceAutoConfiguration.class))
                .withUserConfiguration(TaskConfiguration.class)
                .withBean(ConnectionFactory.class, () -> new ActiveMQConnectionFactory("vm://0"))
                .withPropertyValues("spring.tasks.pool-size=0")
                .run(context -> {
                    Assertions.assertNull(context.getStartupFailure());
                    Assertions.assertInstanceOf(JmsTaskPersistence.class, context.getBean(TaskPersistence.class));
                    Assertions.assertNotNull(context.getBean(TaskService.class));
                });
    }
}
