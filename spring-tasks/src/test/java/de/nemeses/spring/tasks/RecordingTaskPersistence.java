package de.nemeses.spring.tasks;

import java.util.Date;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory {@link TaskPersistence} for tests; stands in for a real backend, which needs JPA or JMS.
 * Records every persisted task and the outcome reported by the workers.
 */
class RecordingTaskPersistence implements TaskPersistence {

    private final BlockingQueue<RecordedTask> due = new LinkedBlockingQueue<>();
    private final List<RecordedTask> persisted = new CopyOnWriteArrayList<>();
    private final BlockingQueue<RecordedTask> finished = new LinkedBlockingQueue<>();
    private final AtomicInteger waiting = new AtomicInteger();
    private final AtomicInteger interrupted = new AtomicInteger();

    @Override
    public void persist(final String beanName, final String payload, final Date earliestExecution) {
        final RecordedTask task = new RecordedTask(beanName, payload, earliestExecution);
        this.persisted.add(task);
        this.due.add(task);
    }

    @Override
    public TaskInfo next() throws InterruptedException {
        this.waiting.incrementAndGet();
        try {
            return this.due.take();
        } catch (final InterruptedException e) {
            this.interrupted.incrementAndGet();
            throw e;
        } finally {
            this.waiting.decrementAndGet();
        }
    }

    /**
     * Forgets all recorded tasks and outcomes.
     */
    void reset() {
        this.due.clear();
        this.persisted.clear();
        this.finished.clear();
    }

    List<RecordedTask> persisted() {
        return this.persisted;
    }

    /**
     * Waits for the next task a worker reported as completed or failed.
     * @return the finished task or <code>null</code> on timeout.
     */
    RecordedTask awaitFinished() throws InterruptedException {
        return this.finished.poll(10, TimeUnit.SECONDS);
    }

    /** Number of tasks reported as completed or failed and not yet taken by {@link #awaitFinished()}. */
    int finishedCount() {
        return this.finished.size();
    }

    /** Number of threads currently blocked in {@link #next()}. */
    int waiting() {
        return this.waiting.get();
    }

    /**
     * Waits until the given number of threads is blocked in {@link #next()}.
     * @return the number of waiting threads, less than expected on timeout.
     */
    int awaitWaiting(final int expected) throws InterruptedException {
        final long deadline = System.currentTimeMillis() + 10_000L;
        while (this.waiting.get() < expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        return this.waiting.get();
    }

    /** Number of {@link #next()} calls left by an interruption. */
    int interrupted() {
        return this.interrupted.get();
    }

    /**
     * A recorded task with its outcome.
     */
    final class RecordedTask implements TaskInfo {
        private final String name;
        private final String data;
        private final Date notBefore;
        private volatile boolean completed;
        private volatile Exception error;

        RecordedTask(final String name, final String data, final Date notBefore) {
            this.name = name;
            this.data = data;
            this.notBefore = notBefore;
        }

        @Override
        public String beanName() {
            return this.name;
        }

        @Override
        public String payload() {
            return this.data;
        }

        @Override
        public Date earliestExecution() {
            return this.notBefore;
        }

        @Override
        public void complete() {
            this.completed = true;
            RecordingTaskPersistence.this.finished.add(this);
        }

        @Override
        public void fail(final Exception cause) {
            this.error = cause;
            RecordingTaskPersistence.this.finished.add(this);
        }

        boolean completed() {
            return this.completed;
        }

        Exception error() {
            return this.error;
        }
    }
}
