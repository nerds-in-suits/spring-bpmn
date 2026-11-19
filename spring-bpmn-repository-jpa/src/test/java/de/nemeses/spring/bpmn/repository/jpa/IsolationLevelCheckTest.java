package de.nemeses.spring.bpmn.repository.jpa;

import java.sql.Connection;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;

/**
 *
 */
class IsolationLevelCheckTest {

    @Test
    void testName() {
        Assertions.assertEquals("READ_COMMITTED", IsolationLevelCheck.name(Connection.TRANSACTION_READ_COMMITTED));
        Assertions.assertEquals("SERIALIZABLE", IsolationLevelCheck.name(Connection.TRANSACTION_SERIALIZABLE));
        Assertions.assertEquals("42", IsolationLevelCheck.name(42));
    }

    /**
     * H2 runs with {@code READ_COMMITTED} by default.
     */
    @Nested
    @SpringBootTest(classes = IsolationLevelCheckTest.TestConfig.class)
    class DefaultIsolation {

        @Autowired
        private IsolationLevelCheck check;

        @Test
        void testReadCommitted() {
            Assertions.assertEquals(Connection.TRANSACTION_READ_COMMITTED, this.check.isolationLevel());
        }
    }

    /**
     * A connection pool configured for another level starts all the same; the check only warns.
     */
    @Nested
    @SpringBootTest(classes = IsolationLevelCheckTest.TestConfig.class,
            properties = "spring.datasource.hikari.transaction-isolation=TRANSACTION_SERIALIZABLE")
    class SerializableIsolation {

        @Autowired
        private IsolationLevelCheck check;

        @Test
        void testSerializable() {
            Assertions.assertEquals(Connection.TRANSACTION_SERIALIZABLE, this.check.isolationLevel());
        }
    }

    /**
     * Test application.
     */
    @Configuration
    @EnableAutoConfiguration(excludeName = "de.nemeses.spring.bpmn.ProcessDefinitionAutoConfiguration")
    static class TestConfig {
    }
}
