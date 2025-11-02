package de.nemeses.spring.tasks.jms;

import org.apache.logging.log4j.message.Message;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.jms.core.JmsTemplate;

import jakarta.jms.ConnectionFactory;

/**
 *
 */
@AutoConfiguration
@ConditionalOnClass({ Message.class, JmsTemplate.class })
@ConditionalOnBean(ConnectionFactory.class)
public class JmsTaskPersistenceAutoConfiguration {

}
