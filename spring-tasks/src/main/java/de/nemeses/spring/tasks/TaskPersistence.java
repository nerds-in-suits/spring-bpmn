package de.nemeses.spring.tasks;

import java.util.Date;

/**
 *
 */
public interface TaskPersistence {

    /**
     * Persists the given task for execution.
     * @param beanName the TaskImplementation bean name to execute.
     * @param payload optional additional payload (preferably as Json string).
     * @param earliestExecution the earliest execution time or <code>null</code> if it can be executed rigth away.
     */
    void persist(String beanName, String payload, Date earliestExecution);

    /**
     * Queries this {@link TaskPersistence} for the next Task due for execution.
     * This call blocks until a Task is due for execution.
     * @return the next task.
     * @throws InterruptedException if the current {@link Thread} got interrupted while waiting for the next task.
     */
    TaskInfo next() throws InterruptedException;

    interface TaskInfo {
        String beanName();
        String payload();
        Date earliestExecution();

        /**
         * Callback to mark the Task as completed.
         */
        void complete();

        /**
         * Callback to mark the Task as failed.
         * @param error the causing exception.
         */
        void fail(Exception error);
    }
}
