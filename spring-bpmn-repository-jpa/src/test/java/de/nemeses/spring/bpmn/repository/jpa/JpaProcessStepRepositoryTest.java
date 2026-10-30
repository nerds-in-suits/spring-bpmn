package de.nemeses.spring.bpmn.repository.jpa;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import de.nemeses.spring.bpmn.repository.NoSuchProcessDefinitionException;
import de.nemeses.spring.bpmn.repository.NoSuchProcessInstanceException;
import de.nemeses.spring.bpmn.repository.NoSuchProcessStepException;
import de.nemeses.spring.bpmn.repository.ProcessDefinitionService;
import de.nemeses.spring.bpmn.repository.ProcessInstance;
import de.nemeses.spring.bpmn.repository.ProcessInstanceRepository;
import de.nemeses.spring.bpmn.repository.ProcessStep;
import de.nemeses.spring.bpmn.repository.ProcessStepRepository;

/**
 *
 */
@SpringBootTest(classes = JpaProcessStepRepositoryTest.TestConfig.class,
        properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class JpaProcessStepRepositoryTest {

    @Autowired
    private ProcessDefinitionService definitions;

    @Autowired
    private ProcessInstanceRepository instances;

    @Autowired
    private ProcessStepRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private ProcessInstance instance;

    @BeforeEach
    void createInstance() throws NoSuchProcessDefinitionException {
        this.definitions.register(new ProcessDefinitionEntity("steps", null, "<s/>"));
        this.instance = this.instances.create(this.definitions.retrieveLatest("steps"), Map.of());
    }

    @Test
    void testCreateAndRetrieve() throws NoSuchProcessInstanceException, NoSuchProcessStepException {
        final ProcessStep created = this.repository.create(this.instance.id(), "t", "f", null);

        Assertions.assertNotNull(created.id());
        final ProcessStep retrieved = this.repository.retrieve(created.id());
        Assertions.assertEquals(created.id(), retrieved.id());
        Assertions.assertEquals(this.instance.id(), retrieved.processInstanceId());
        Assertions.assertEquals("t", retrieved.nodeId());
        Assertions.assertEquals("f", retrieved.incomingFlowId());
        Assertions.assertNotNull(retrieved.startDate());
        Assertions.assertNull(retrieved.endDate());
        Assertions.assertNull(this.repository.create(this.instance.id(), "s", null, null).incomingFlowId());
    }

    @Test
    void testChildrenOfSubProcessStep() throws NoSuchProcessInstanceException, NoSuchProcessStepException {
        final ProcessStep subProcess = this.repository.create(this.instance.id(), "sp", null, null);
        final ProcessStep start = this.repository.create(this.instance.id(), "ss", null, subProcess.id());
        final ProcessStep task = this.repository.create(this.instance.id(), "t", "f", subProcess.id());
        this.repository.create(this.instance.id(), "other", null, null);

        Assertions.assertNull(this.repository.retrieve(subProcess.id()).parentStepId());
        Assertions.assertEquals(subProcess.id(), this.repository.retrieve(start.id()).parentStepId());
        Assertions.assertEquals(Set.of(start.id(), task.id()),
                Set.copyOf(JpaProcessStepRepositoryTest.ids(this.repository.children(subProcess.id()))));
        Assertions.assertTrue(this.repository.children(start.id()).isEmpty());

        this.repository.complete(List.of(start.id()), new Date());
        Assertions.assertEquals(List.of(task.id()), this.repository.children(subProcess.id()).stream()
                .filter(step -> step.endDate() == null).map(ProcessStep::id).toList());
    }

    @Test
    void testCreateForUnknownInstanceFails() {
        Assertions.assertThrows(NoSuchProcessInstanceException.class,
                () -> this.repository.create(UUID.randomUUID(), "t", null, null));
    }

    @Test
    void testRetrieveOrCompleteUnknownStepFails() {
        Assertions.assertThrows(NoSuchProcessStepException.class, () -> this.repository.retrieve(UUID.randomUUID()));
        Assertions.assertThrows(NoSuchProcessStepException.class,
                () -> this.repository.complete(List.of(UUID.randomUUID()), new Date()));
    }

    @Test
    void testPendingCountAndComplete() throws NoSuchProcessInstanceException, NoSuchProcessStepException {
        final ProcessStep a = this.repository.create(this.instance.id(), "join", "fa", null);
        final ProcessStep b = this.repository.create(this.instance.id(), "join", "fb", null);
        final ProcessStep other = this.repository.create(this.instance.id(), "t", null, null);

        // steps created in the same millisecond have no defined order
        Assertions.assertEquals(Set.of(a.id(), b.id()), Set.copyOf(JpaProcessStepRepositoryTest.ids(
                this.repository.pending(this.instance.id(), "join"))));
        Assertions.assertEquals(3, this.repository.countPending(this.instance.id()));

        final Date endDate = new Date();
        this.repository.complete(List.of(a.id(), other.id()), endDate);

        Assertions.assertEquals(List.of(b.id()), JpaProcessStepRepositoryTest.ids(
                this.repository.pending(this.instance.id(), "join")));
        Assertions.assertTrue(this.repository.pending(this.instance.id(), "t").isEmpty());
        Assertions.assertEquals(1, this.repository.countPending(this.instance.id()));
        Assertions.assertEquals(endDate.getTime(), this.repository.retrieve(a.id()).endDate().getTime());
        Assertions.assertEquals(3, this.repository.steps(this.instance.id()).size());
        Assertions.assertTrue(this.repository.steps(UUID.randomUUID()).isEmpty());
    }

    @Test
    void testPendingIgnoresStepsCompletedByOtherTransaction() throws NoSuchProcessInstanceException {
        final ProcessStep step = this.repository.create(this.instance.id(), "t", null, null);
        final TransactionTemplate outer = new TransactionTemplate(this.transactionManager);
        final TransactionTemplate inner = new TransactionTemplate(this.transactionManager);
        inner.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        final List<ProcessStep> pending = outer.execute(status -> {
            try {
                // loaded (still pending) into this transaction before another one completes it
                Assertions.assertNull(this.repository.retrieve(step.id()).endDate());
                inner.executeWithoutResult(innerStatus -> {
                    try {
                        this.repository.complete(List.of(step.id()), new Date());
                    } catch (final NoSuchProcessStepException e) {
                        throw new IllegalStateException(e);
                    }
                });
                return this.repository.pending(this.instance.id(), "t");
            } catch (final NoSuchProcessStepException e) {
                throw new IllegalStateException(e);
            }
        });
        Assertions.assertEquals(List.of(), pending);
        Assertions.assertEquals(0, this.repository.countPending(this.instance.id()));
    }

    private static List<UUID> ids(final List<ProcessStep> steps) {
        return steps.stream().map(ProcessStep::id).toList();
    }

    /**
     * Test application.
     */
    @Configuration
    @EnableAutoConfiguration(excludeName = "de.nemeses.spring.bpmn.ProcessDefinitionAutoConfiguration")
    static class TestConfig {
    }
}
