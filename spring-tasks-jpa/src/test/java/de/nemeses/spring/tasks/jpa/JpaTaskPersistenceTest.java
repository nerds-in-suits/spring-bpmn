package de.nemeses.spring.tasks.jpa;

import java.time.Duration;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import de.nemeses.spring.tasks.TaskPersistence;
import de.nemeses.spring.tasks.TaskPersistence.TaskInfo;
import de.nemeses.spring.tasks.jpa.application.TestApplication;

/**
 * Tests the auto-configured {@link JpaTaskPersistence} against an H2 database.
 */
@SpringBootTest(classes = TestApplication.class,
        properties = { "spring.jpa.hibernate.ddl-auto=create-drop", "spring.tasks.jpa.max-results=2" })
class JpaTaskPersistenceTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    private TaskPersistence persistence;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clear() {
        this.jdbc.update("delete from spring_tasks");
    }

    @Test
    void testAutoConfiguration() {
        Assertions.assertInstanceOf(JpaTaskPersistence.class, this.persistence);
    }

    @Test
    void testPersistAndClaim() {
        final Date due = new Date(System.currentTimeMillis() - 1000L);
        this.persistence.persist("claimed", "{\"a\":1}", due);
        Assertions.assertEquals("PENDING", this.status("claimed"));

        final TaskInfo task = this.next();

        Assertions.assertEquals("claimed", task.beanName());
        Assertions.assertEquals("{\"a\":1}", task.payload());
        Assertions.assertEquals(due.getTime(), task.earliestExecution().getTime());
        Assertions.assertEquals("RUNNING", this.status("claimed"));
        task.complete();
    }

    @Test
    void testComplete() {
        this.persistence.persist("completed", null, null);

        final TaskInfo task = this.next();
        Assertions.assertNull(task.payload());
        Assertions.assertNull(task.earliestExecution());
        task.complete();

        Assertions.assertEquals("COMPLETED", this.status("completed"));
        Assertions.assertNull(this.error("completed"));
    }

    @Test
    void testFail() {
        this.persistence.persist("failed", null, null);

        this.next().fail(new IllegalStateException("broken"));

        Assertions.assertEquals("FAILED", this.status("failed"));
        Assertions.assertTrue(this.error("failed").contains("IllegalStateException: broken"));
    }

    @Test
    void testFutureTaskIsNotClaimed() {
        this.persistence.persist("future", null, new Date(System.currentTimeMillis() + 3_600_000L));
        this.persistence.persist("now", null, null);

        final TaskInfo task = this.next();

        Assertions.assertEquals("now", task.beanName());
        Assertions.assertEquals("PENDING", this.status("future"));
        task.complete();
    }

    @Test
    void testMaxResultsAndOrder() {
        final long now = System.currentTimeMillis();
        this.persistence.persist("second", null, new Date(now - 1000L));
        this.persistence.persist("third", null, new Date(now - 1000L));
        this.persistence.persist("first", null, new Date(now - 2000L));
        this.persistence.persist("nulls-first", null, null);

        final TaskInfo first = this.next();
        Assertions.assertEquals("nulls-first", first.beanName());
        Assertions.assertEquals(List.of("RUNNING", "RUNNING", "PENDING", "PENDING"),
                List.of(this.status("nulls-first"), this.status("first"), this.status("second"),
                        this.status("third")));

        Assertions.assertEquals("first", this.next().beanName());
        Assertions.assertEquals("PENDING", this.status("second"));
        Assertions.assertEquals("second", this.next().beanName());
        Assertions.assertEquals("RUNNING", this.status("third"));
        Assertions.assertEquals("third", this.next().beanName());
    }

    private TaskInfo next() {
        return Assertions.assertTimeoutPreemptively(JpaTaskPersistenceTest.TIMEOUT, this.persistence::next);
    }

    private String status(final String beanName) {
        return this.jdbc.queryForObject("select status from spring_tasks where beanname = ?", String.class,
                beanName);
    }

    private String error(final String beanName) {
        return this.jdbc.queryForObject("select error from spring_tasks where beanname = ?", String.class,
                beanName);
    }
}
