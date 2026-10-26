package de.nemeses.spring.tasks.jpa;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;
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

    JpaTaskPersistence(final EntityManager entityManager, final TransactionTemplate transactions) {
        this.entityManager = entityManager;
        this.transactions = transactions;
    }

    @Override
    @Transactional
    public void persist(final String beanName, final String payload, final Date earliestExecution) {
        LoggerFactory.getLogger(this.getClass()).info("Persisting task {}", beanName);
        this.entityManager.persist(new TaskEntity(beanName, payload, earliestExecution));
    }

    @Override
    public TaskInfo next() throws InterruptedException {
        while (true) {
            final TaskInfo claimed = this.transactions.execute(status -> this.claimNext());
            if (claimed != null) {
                return claimed;
            }
            Thread.sleep(POLL_INTERVAL_MILLIS);
        }
    }

    /**
     * Claims the oldest due pending task by marking it as running.
     * @return the claimed task or <code>null</code> if none is due.
     */
    private TaskInfo claimNext() {
        final List<TaskEntity> due = this.entityManager
                .createQuery("select t from TaskEntity t where t.status = :status"
                        + " and (t.execution is null or t.execution <= :now)"
                        + " order by t.execution asc nulls first, t.id asc", TaskEntity.class)
                .setParameter("status", TaskEntity.Status.PENDING)
                .setParameter("now", new Date())
                .setMaxResults(1)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList();
        if (due.isEmpty()) {
            return null;
        }
        final TaskEntity entity = due.get(0);
        entity.setStatus(TaskEntity.Status.RUNNING);
        return new JpaTaskInfo(entity.getId(), entity.getBeanName(), entity.getPayload(), entity.getExecution());
    }

    private void finish(final Long id, final TaskEntity.Status status, final String error) {
        this.transactions.executeWithoutResult(tx -> {
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
