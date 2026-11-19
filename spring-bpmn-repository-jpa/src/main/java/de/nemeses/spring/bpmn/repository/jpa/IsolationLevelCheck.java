package de.nemeses.spring.bpmn.repository.jpa;

import java.sql.Connection;

import org.hibernate.Session;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;

/**
 * Checks once on startup, after all singletons are created and before the task workers start, that the JPA
 * transactions run with the isolation level {@code READ_COMMITTED}, which the engine is built for: its steps lock the
 * instance and then must see what the steps committed before them.
 * Any other level only logs a warning and does not stop the startup; neither does a failing check.
 * The level is read from the JDBC connection of a JPA transaction, so it reflects the database or connection pool
 * default (e.g. {@code spring.datasource.hikari.transaction-isolation}).
 */
class IsolationLevelCheck implements SmartInitializingSingleton {

    private final EntityManager entityManager;
    private final TransactionTemplate transactions;

    IsolationLevelCheck(final EntityManager entityManager, final TransactionTemplate transactions) {
        this.entityManager = entityManager;
        this.transactions = transactions;
    }

    @Override
    public void afterSingletonsInstantiated() {
        try {
            final int isolation = this.isolationLevel();
            if (isolation == Connection.TRANSACTION_READ_COMMITTED) {
                LoggerFactory.getLogger(this.getClass()).debug("JPA transactions run with isolation READ_COMMITTED");
            } else {
                LoggerFactory.getLogger(this.getClass()).warn(
                        "JPA transactions run with isolation {} instead of READ_COMMITTED;"
                                + " process instances may not see the steps committed before them",
                        IsolationLevelCheck.name(isolation));
            }
        } catch (final PersistenceException | TransactionException e) {
            LoggerFactory.getLogger(this.getClass()).warn("Cannot check the isolation level of JPA transactions", e);
        }
    }

    /**
     * Reads the isolation level of the JDBC connection of a new JPA transaction.
     * @return one of the {@code Connection.TRANSACTION_*} constants.
     */
    int isolationLevel() {
        final Integer isolation = this.transactions.execute(_ -> this.entityManager.unwrap(Session.class)
                .doReturningWork(Connection::getTransactionIsolation));
        return isolation == null ? Connection.TRANSACTION_NONE : isolation;
    }

    /**
     * @param isolation one of the {@code Connection.TRANSACTION_*} constants.
     * @return the name of the isolation level.
     */
    static String name(final int isolation) {
        return switch (isolation) {
        case Connection.TRANSACTION_NONE -> "NONE";
        case Connection.TRANSACTION_READ_UNCOMMITTED -> "READ_UNCOMMITTED";
        case Connection.TRANSACTION_READ_COMMITTED -> "READ_COMMITTED";
        case Connection.TRANSACTION_REPEATABLE_READ -> "REPEATABLE_READ";
        case Connection.TRANSACTION_SERIALIZABLE -> "SERIALIZABLE";
        default -> String.valueOf(isolation);
        };
    }
}
