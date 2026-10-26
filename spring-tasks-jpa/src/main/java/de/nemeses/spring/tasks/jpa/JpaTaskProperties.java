package de.nemeses.spring.tasks.jpa;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration properties of the JPA task persistence.
 *
 * @param maxResults maximum number of due tasks fetched (and kept in memory) per database query.
 */
@ConfigurationProperties(prefix = "spring.tasks.jpa")
public record JpaTaskProperties(@DefaultValue("20") int maxResults) {
}
