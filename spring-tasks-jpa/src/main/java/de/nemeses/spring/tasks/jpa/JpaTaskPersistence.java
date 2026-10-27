package de.nemeses.spring.tasks.jpa;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.List;

import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import de.nemeses.spring.tasks.TaskPersistence;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

/**
 *
 */
public class JpaTaskPersistence implements TaskPersistence {
    private static final long POLL_INTERVAL_MILLIS = 1000L;

    private final EntityManager entityManager;
    private final TransactionTemplate transactions;
    private final int maxResults;
    /** Already claimed (RUNNING) tasks that are served without querying the database. */
    private final Deque<TaskInfo> buffer;

    JpaTaskPersistence(final EntityManager entityManager, final TransactionTemplate transactions,
            final int maxResults) {
        this.entityManager = entityManager;
        this.transactions = transactions;
        this.maxResults = maxResults;
        this.buffer = new ArrayDeque<>(maxResults);
    }

    @Override
    @Transactional
    public void persist(final String beanName, final String payload, final Date earliestExecution) {
        LoggerFactory.getLogger(this.getClass()).debug("Persisting task {}", beanName);
        this.entityManager.persist(new TaskEntity(beanName, payload, earliestExecution));
    }

    @Override
    public TaskInfo next() throws InterruptedException {
        while (true) {
            final TaskInfo task = this.pollBuffered();
            if (task != null) {
                return task;
            }
            Thread.sleep(JpaTaskPersistence.POLL_INTERVAL_MILLIS);
        }
    }

    /**
     * Serves the next task from the in-memory list; queries the database only if that list is empty.
     * @return the next task or <code>null</code> if none is due.
     */
    private synchronized TaskInfo pollBuffered() {
        if (this.buffer.isEmpty()) {
            final List<TaskInfo> claimed = this.transactions.execute(_ -> this.claimNext());
            if (claimed != null) {
                this.buffer.addAll(claimed);
            }
        }
        return this.buffer.poll();
    }

    /**
     * Claims up to the configured number of the oldest due pending tasks by marking them as running.
     * @return the claimed tasks, empty if none is due.
     */
    private List<TaskInfo> claimNext() {
        final List<TaskEntity> due = this.entityManager
                .createQuery("select t from TaskEntity t where t.status = :status"
                        + " and (t.execution is null or t.execution <= :now)"
                        + " order by t.execution asc nulls first, t.id asc", TaskEntity.class)
                .setParameter("status", TaskEntity.Status.PENDING)
                .setParameter("now", new Date())
                .setMaxResults(this.maxResults)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList();
        final List<TaskInfo> claimed = new ArrayList<>(due.size());
        for (final TaskEntity entity : due) {
            entity.setStatus(TaskEntity.Status.RUNNING);
            claimed.add(new JpaTaskInfo(entity.getId(), entity.getBeanName(), entity.getPayload(),
                    entity.getExecution()));
        }
        return claimed;
    }

    private void finish(final Long id, final TaskEntity.Status status, final String error) {
        this.transactions.executeWithoutResult(_ -> {
            final TaskEntity entity = this.entityManager.find(TaskEntity.class, id);
            entity.setStatus(status);
            entity.setError(error);
        });
    }

    private final class JpaTaskInfo implements TaskInfo {
        private final Long id;
        private final String task;
        private final String data;
        private final Date due;

        JpaTaskInfo(final Long id, final String task, final String data, final Date due) {
            this.id = id;
            this.task = task;
            this.data = data;
            this.due = due;
        }

        @Override
        public String beanName() {
            return this.task;
        }

        @Override
        public String payload() {
            return this.data;
        }

        @Override
        public Date earliestExecution() {
            return this.due;
        }

        @Override
        public void complete() {
            JpaTaskPersistence.this.finish(this.id, TaskEntity.Status.COMPLETED, null);
        }

        @Override
        public void fail(final Exception error) {
            final StringWriter trace = new StringWriter();
            error.printStackTrace(new PrintWriter(trace));
            JpaTaskPersistence.this.finish(this.id, TaskEntity.Status.FAILED, trace.toString());
        }
    }
}
