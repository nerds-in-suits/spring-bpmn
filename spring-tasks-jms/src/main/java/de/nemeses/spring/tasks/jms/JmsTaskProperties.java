package de.nemeses.spring.tasks.jms;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration properties of the JMS task persistence.
 *
 * @param queue name of the JMS queue tasks are sent to and received from.
 */
@ConfigurationProperties(prefix = "spring.tasks.jms")
public record JmsTaskProperties(@DefaultValue("spring.tasks") String queue) {
}
