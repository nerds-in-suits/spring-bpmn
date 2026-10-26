package de.nemeses.spring.tasks.jms;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.jms.core.JmsTemplate;

import de.nemeses.spring.tasks.TaskPersistence;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.Message;

/**
 *
 */
@AutoConfiguration
@ConditionalOnClass({ Message.class, JmsTemplate.class })
@ConditionalOnBean(ConnectionFactory.class)
public class JmsTaskPersistenceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(TaskPersistence.class)
    JmsTaskPersistence jmsTaskPersistence(final ConnectionFactory connectionFactory,
            @Value("${spring.tasks.jms.queue:spring.tasks}") final String queueName) {
        return new JmsTaskPersistence(connectionFactory, queueName);
    }
}
