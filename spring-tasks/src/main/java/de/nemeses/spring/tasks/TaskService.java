package de.nemeses.spring.tasks;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * Schedules tasks and executes them.
 * On application start a pool of {@code spring.tasks.pool-size} worker threads (default 10) is started.
 * Each worker repeatedly takes the next due task from the {@link TaskPersistence}, executes it with the
 * {@link TaskImplementation} bean of the task's bean name and reports the outcome via
 * {@link TaskPersistence.TaskInfo#complete()} or {@link TaskPersistence.TaskInfo#fail(Exception)}.
 * The workers are interrupted and awaited when the application context stops.
 */
@Component
public class TaskService implements SmartLifecycle {

    /** Pause after the persistence failed to deliver the next task, to avoid a busy loop. */
    private static final long ERROR_BACKOFF_MILLIS = 1000L;
    /** Maximum time to wait for the workers to finish on shutdown. */
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 30L;

    private static final Logger LOG = LoggerFactory.getLogger(TaskService.class);

    private final TaskPersistence persistence;
    private final Map<String, TaskImplementation<?>> tasks;
    private final int poolSize;
    private ExecutorService workers;

    TaskService(final TaskPersistence persistence, final Map<String, TaskImplementation<?>> tasks,
            @Value("${spring.tasks.pool-size:10}") final int poolSize) {
        if (poolSize < 0) {
            throw new IllegalArgumentException("spring.tasks.pool-size must not be negative: " + poolSize);
        }
        this.persistence = persistence;
        LOG.info("Using task persistence {}.", persistence);
        this.tasks = tasks;
        LOG.debug("Registered tasks: {}", this.tasks);
        this.poolSize = poolSize;
    }

    public void trigger(final String beanName, final Object payload) {
        this.schedule(beanName, payload, null);
    }

    public void schedule(final String beanName, final Object payload, final Date earliestExecution) {
        final String serializedPayload = this.taskImplementation(beanName).serializePayloadObj(payload);
        this.persistence.persist(beanName, serializedPayload, earliestExecution);
    }

    /**
     * Starts the worker threads.
     */
    @Override
    public synchronized void start() {
        if (this.workers != null) {
            return;
        }
        LOG.info("Starting {} task worker threads.", this.poolSize);
        this.workers = Executors.newFixedThreadPool(Math.max(1, this.poolSize),
            Thread.ofPlatform().name("spring-tasks-worker-", 1).factory());
        for (int i = 0; i < this.poolSize; i++) {
            this.workers.execute(this::work);
        }
    }

    /**
     * Interrupts the worker threads and waits for them to terminate.
     */
    @Override
    public synchronized void stop() {
        if (this.workers == null) {
            return;
        }
        LOG.info("Stopping task worker threads.");
        this.workers.shutdownNow();
        try {
            if (!this.workers.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                LOG.warn("Task worker threads did not terminate within {} seconds.", SHUTDOWN_TIMEOUT_SECONDS);
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        this.workers = null;
    }

    @Override
    public synchronized boolean isRunning() {
        return this.workers != null;
    }

    /**
     * Worker loop: executes tasks until the thread is interrupted.
     */
    @SuppressWarnings({"checkstyle:IllegalCatch", "PMD.AvoidCatchingGenericException"})
    private void work() {
        while (!Thread.currentThread().isInterrupted()) {
            final TaskPersistence.TaskInfo info;
            try {
                info = this.persistence.next();
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (final RuntimeException e) {
                LOG.error("Failed to retrieve the next task.", e);
                if (!this.pause()) {
                    break;
                }
                continue;
            }
            this.execute(info);
        }
        LOG.debug("Task worker stopped.");
    }

    @SuppressWarnings({"checkstyle:IllegalCatch", "PMD.AvoidCatchingGenericException"})
    private void execute(final TaskPersistence.TaskInfo info) {
        try {
            LOG.debug("Executing task {}.", info.beanName());
            this.taskImplementation(info.beanName()).executeSerialized(info.payload());
        } catch (final Exception e) {
            LOG.debug("Task {} failed.", info.beanName(), e);
            this.report(info, e);
            return;
        }
        this.report(info, null);
    }

    @SuppressWarnings({"checkstyle:IllegalCatch", "PMD.AvoidCatchingGenericException"})
    private void report(final TaskPersistence.TaskInfo info, final Exception error) {
        try {
            if (error == null) {
                info.complete();
            } else {
                info.fail(error);
            }
        } catch (final RuntimeException e) {
            LOG.error("Failed to report the outcome of task {}.", info.beanName(), e);
        }
    }

    private boolean pause() {
        try {
            Thread.sleep(ERROR_BACKOFF_MILLIS);
            return true;
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private TaskImplementation<?> taskImplementation(final String beanName) {
        final TaskImplementation<?> ret = this.tasks.get(beanName);
        if(ret == null) {
            throw new IllegalArgumentException("No task implementation registered for bean name '"+beanName+"'.");
        }
        return ret;
    }
}
