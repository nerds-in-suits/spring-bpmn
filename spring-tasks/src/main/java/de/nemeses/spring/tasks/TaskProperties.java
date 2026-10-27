package de.nemeses.spring.tasks;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration properties of the task execution in {@link TaskService}.
 *
 * @param poolSize number of worker threads; <code>0</code> starts no worker, i.e. tasks are only scheduled.
 * @param errorBackoff pause of a worker after the persistence failed to deliver the next task.
 * @param shutdownTimeout maximum time to wait for the workers to finish on shutdown.
 */
@ConfigurationProperties(prefix = "spring.tasks")
public record TaskProperties(@DefaultValue("10") int poolSize, @DefaultValue("1s") Duration errorBackoff,
        @DefaultValue("30s") Duration shutdownTimeout) {

    /**
     * Validates the properties.
     */
    public TaskProperties {
        if (poolSize < 0) {
            throw new IllegalArgumentException("spring.tasks.pool-size must not be negative: " + poolSize);
        }
        if (errorBackoff == null || errorBackoff.isNegative()) {
            throw new IllegalArgumentException("spring.tasks.error-backoff must not be negative: " + errorBackoff);
        }
        if (shutdownTimeout == null || shutdownTimeout.isNegative()) {
            throw new IllegalArgumentException("spring.tasks.shutdown-timeout must not be negative: "
                + shutdownTimeout);
        }
    }
}
