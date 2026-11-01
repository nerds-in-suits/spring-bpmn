package de.nemeses.spring.tasks;

import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanNotOfRequiredTypeException;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * Schedules tasks and executes them.
 * On application start a pool of {@code spring.tasks.pool-size} worker threads (default 10) is started;
 * with a pool size of <code>0</code> no worker is started and tasks are only scheduled.
 * Each worker repeatedly takes the next due task from the {@link TaskPersistence}, executes it with the
 * {@link TaskImplementation} bean of the task's bean name and reports the outcome via
 * {@link TaskPersistence.TaskInfo#complete()} or {@link TaskPersistence.TaskInfo#fail(Exception)}.
 * The workers are interrupted and awaited when the application context stops.
 * All settings are bound from {@link TaskProperties}.
 * <p>
 * The {@link TaskImplementation} beans are looked up by name in the bean factory each time a task is scheduled or
 * executed, not injected, so a task implementation may itself depend on this service.
 */
@Component
public class TaskService implements SmartLifecycle {

    private final TaskPersistence persistence;
    private final ListableBeanFactory beanFactory;
    private final TaskProperties properties;
    private boolean running;
    private ExecutorService workers;

    TaskService(final TaskPersistence persistence, final ListableBeanFactory beanFactory,
            final TaskProperties properties) {
        this.persistence = persistence;
        LoggerFactory.getLogger(this.getClass()).info("Using task persistence {}.", persistence);
        this.beanFactory = beanFactory;
        LoggerFactory.getLogger(this.getClass()).debug("Registered tasks: {}",
            Arrays.asList(beanFactory.getBeanNamesForType(TaskImplementation.class, true, false)));
        this.properties = properties;
    }

    /**
     * Schedules a task for immediate execution.
     *
     * @param beanName the bean name of the {@link TaskImplementation}
     * @param payload the payload, serialized by that implementation
     * @throws IllegalArgumentException if there is no bean of that name or it is no {@link TaskImplementation}
     */
    public void trigger(final String beanName, final Object payload) {
        this.schedule(beanName, payload, null);
    }

    /**
     * Schedules a task.
     *
     * @param beanName the bean name of the {@link TaskImplementation}
     * @param payload the payload, serialized by that implementation
     * @param earliestExecution the earliest time to execute the task, <code>null</code> for immediately
     * @throws IllegalArgumentException if there is no bean of that name or it is no {@link TaskImplementation}
     */
    public void schedule(final String beanName, final Object payload, final Date earliestExecution) {
        final String serializedPayload = this.taskImplementation(beanName).serializePayloadObj(payload);
        this.persistence.persist(beanName, serializedPayload, earliestExecution);
    }

    /**
     * Starts the worker threads, none if the pool size is <code>0</code>.
     */
    @Override
    public synchronized void start() {
        if (this.running) {
            return;
        }
        this.running = true;
        final int poolSize = this.properties.poolSize();
        LoggerFactory.getLogger(this.getClass()).info("Starting {} task worker threads.", poolSize);
        if (poolSize == 0) {
            return;
        }
        this.workers = Executors.newFixedThreadPool(poolSize,
            Thread.ofPlatform().name("spring-tasks-worker-", 1).factory());
        for (int i = 0; i < poolSize; i++) {
            this.workers.execute(this::work);
        }
    }

    /**
     * Interrupts the worker threads and waits for them to terminate.
     */
    @Override
    public synchronized void stop() {
        if (!this.running) {
            return;
        }
        this.running = false;
        if (this.workers == null) {
            return;
        }
        try {
            LoggerFactory.getLogger(this.getClass()).info("Stopping task worker threads.");
            this.workers.shutdownNow();
            if (!this.workers.awaitTermination(this.properties.shutdownTimeout().toMillis(), TimeUnit.MILLISECONDS)) {
                LoggerFactory.getLogger(this.getClass()).warn("Task worker threads did not terminate within {}.",
                    this.properties.shutdownTimeout());
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        this.workers = null;
    }

    @Override
    public synchronized boolean isRunning() {
        return this.running;
    }

    /**
     * Worker loop: executes tasks until the thread is interrupted.
     * {@link #execute(TaskPersistence.TaskInfo)} handles and reports every failure of the task itself, so a
     * {@link RuntimeException} caught here always comes from {@link TaskPersistence#next()}.
     */
    @SuppressWarnings({"checkstyle:IllegalCatch", "PMD.AvoidCatchingGenericException"})
    private void work() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                final TaskPersistence.TaskInfo info = this.persistence.next();
                this.execute(info);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (final RuntimeException e) {
                LoggerFactory.getLogger(this.getClass()).error("Failed to retrieve the next task.", e);
                this.pause();
            }
        }
        LoggerFactory.getLogger(this.getClass()).info("Task worker stopped.");
    }

    /**
     * Executes the task and reports its outcome. Never throws: every exception of the task is reported via
     * {@link TaskPersistence.TaskInfo#fail(Exception)}, failures to report are logged.
     */
    @SuppressWarnings({"checkstyle:IllegalCatch", "PMD.AvoidCatchingGenericException"})
    private void execute(final TaskPersistence.TaskInfo info) {
        try {
            LoggerFactory.getLogger(this.getClass()).debug("Executing task {}.", info.beanName());
            this.taskImplementation(info.beanName()).executeSerialized(info.payload());
            this.report(info, null);
        } catch (final Exception e) {
            LoggerFactory.getLogger(this.getClass()).debug("Task {} failed.", info.beanName(), e);
            this.report(info, e);
        }
    }

    /**
     * Reports the outcome of a task. Never throws, failures are logged.
     */
    @SuppressWarnings({"checkstyle:IllegalCatch", "PMD.AvoidCatchingGenericException"})
    private void report(final TaskPersistence.TaskInfo info, final Exception error) {
        try {
            if (error == null) {
                info.complete();
            } else {
                info.fail(error);
            }
        } catch (final RuntimeException e) {
            LoggerFactory.getLogger(this.getClass()).error("Failed to report the outcome of task {}.",
                info.beanName(), e);
        }
    }

    /**
     * Pauses for {@code spring.tasks.error-backoff}; an interrupt ends the pause and is kept as interrupt flag,
     * which ends the worker loop.
     */
    private void pause() {
        try {
            Thread.sleep(this.properties.errorBackoff());
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Looks up the task implementation in the bean factory.
     *
     * @throws IllegalArgumentException if there is no bean of that name or it is no {@link TaskImplementation}
     */
    private TaskImplementation<?> taskImplementation(final String beanName) {
        try {
            return this.beanFactory.getBean(beanName, TaskImplementation.class);
        } catch (final NoSuchBeanDefinitionException e) {
            throw new IllegalArgumentException("No task implementation registered for bean name '" + beanName + "'.",
                e);
        } catch (final BeanNotOfRequiredTypeException e) {
            throw new IllegalArgumentException("Bean '" + beanName + "' is no task implementation.", e);
        }
    }
}
