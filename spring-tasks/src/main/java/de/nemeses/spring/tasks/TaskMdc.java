package de.nemeses.spring.tasks;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.MDC;

/**
 * Keys of the SLF4J {@link MDC} entries set while tasks run, and {@link Scope}s that set entries and restore them.
 * <p>
 * {@link TaskService} sets {@link #TASK_ID} for the duration of each task execution, including the report of its
 * outcome, on whatever worker thread runs it. Modules built on tasks (such as the BPMN engine) add their own keys with
 * {@link #put(Map)}. The key names are fixed constants, not configuration, so log patterns and log processing can rely
 * on them.
 * <p>
 * A scope restores the values the keys had before it was opened (or removes keys that were not set), so nested scopes
 * and pooled threads keep a clean MDC. Open and close a scope on the same thread, with try-with-resources.
 */
public final class TaskMdc {

    /** MDC key of the id of the executed task ({@link TaskPersistence.TaskInfo#taskId()}). */
    public static final String TASK_ID = "taskId";

    private TaskMdc() {
    }

    /**
     * Sets one MDC entry until the returned scope is closed.
     *
     * @param key the MDC key
     * @param value the value; {@code null} removes the key for the duration of the scope
     * @return the scope that restores the previous value when closed
     */
    public static Scope put(final String key, final String value) {
        final Map<String, String> entries = new HashMap<>();
        entries.put(key, value);
        return TaskMdc.put(entries);
    }

    /**
     * Sets several MDC entries until the returned scope is closed.
     *
     * @param entries the keys and values; a {@code null} value removes its key for the duration of the scope
     * @return the scope that restores the previous values when closed
     */
    public static Scope put(final Map<String, String> entries) {
        final Map<String, String> previous = new HashMap<>();
        for (final Map.Entry<String, String> entry : entries.entrySet()) {
            previous.put(entry.getKey(), MDC.get(entry.getKey()));
            TaskMdc.set(entry.getKey(), entry.getValue());
        }
        return () -> previous.forEach(TaskMdc::set);
    }

    private static void set(final String key, final String value) {
        if (value == null) {
            MDC.remove(key);
        } else {
            MDC.put(key, value);
        }
    }

    /**
     * MDC entries set by {@link TaskMdc#put}; closing restores the previous values.
     */
    @FunctionalInterface
    public interface Scope extends AutoCloseable {

        /**
         * Restores the values the keys had before the scope was opened.
         */
        @Override
        void close();
    }
}
