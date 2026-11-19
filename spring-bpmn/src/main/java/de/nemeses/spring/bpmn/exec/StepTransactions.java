package de.nemeses.spring.bpmn.exec;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;
import java.util.UUID;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import de.nemeses.spring.bpmn.repository.NoSuchProcessStepException;
import de.nemeses.spring.bpmn.repository.ProcessStep;
import de.nemeses.spring.bpmn.repository.ProcessStepRepository;

/**
 * Runs the execution of a {@link ProcessStep} in a transaction and records its failure on the step.
 * <p>
 * The transaction is started with the {@link PlatformTransactionManager} of the application context, not by a proxy;
 * without a transaction manager the work runs without transaction. If the work fails, the transaction is rolled back
 * first and then the failure is recorded on the step ({@link ProcessStepRepository#fail}): its time, the message of
 * the exception and its stack trace with all causes. So the record survives the rollback of the step.
 */
@Component
class StepTransactions {

    private final ProcessStepRepository steps;
    private final TransactionOperations transactions;

    StepTransactions(final ProcessStepRepository steps,
            final ObjectProvider<PlatformTransactionManager> transactionManager) {
        this.steps = steps;
        final PlatformTransactionManager manager = transactionManager.getIfAvailable();
        this.transactions = manager == null
                ? TransactionOperations.withoutTransaction()
                : new TransactionTemplate(manager);
    }

    /**
     * Runs the work of a step in one transaction. If it throws a {@link ProcessEngineException} or a
     * {@link RuntimeException}, the transaction is rolled back and the exception is recorded on the step; a failure to
     * record it is logged and does not replace the failure of the step.
     *
     * @param stepId the id of the step the work executes
     * @param work the work
     * @throws ProcessEngineException the exception of the work, or one with its {@link RuntimeException} as cause
     */
    @SuppressWarnings({"checkstyle:IllegalCatch", "PMD.AvoidCatchingGenericException"})
    void execute(final UUID stepId, final StepWork work) throws ProcessEngineException {
        final Exception failure = this.transactions.execute(status -> {
            try {
                work.run();
                return null;
            } catch (final ProcessEngineException | RuntimeException e) {
                status.setRollbackOnly();
                return e;
            }
        });
        if (failure == null) {
            return;
        }
        this.record(stepId, failure);
        if (failure instanceof final ProcessEngineException e) {
            throw e;
        }
        throw new ProcessEngineException(failure.getMessage(), failure);
    }

    /**
     * Records the failure on the step; never throws, a failure to record it is logged.
     */
    @SuppressWarnings({"checkstyle:IllegalCatch", "PMD.AvoidCatchingGenericException"})
    private void record(final UUID stepId, final Exception failure) {
        final StringWriter trace = new StringWriter();
        try (PrintWriter writer = new PrintWriter(trace)) {
            failure.printStackTrace(writer);
        }
        try {
            this.steps.fail(stepId, new Date(), failure.getMessage(), trace.toString());
        } catch (final NoSuchProcessStepException e) {
            LoggerFactory.getLogger(this.getClass()).warn("Cannot record the failure of process step {}, which does"
                    + " not exist.", stepId);
        } catch (final RuntimeException e) {
            LoggerFactory.getLogger(this.getClass()).error("Cannot record the failure of process step {}.", stepId,
                    e);
        }
    }

    /**
     * The work of a step.
     */
    @FunctionalInterface
    interface StepWork {

        /**
         * @throws ProcessEngineException if the step failed
         */
        void run() throws ProcessEngineException;
    }
}
