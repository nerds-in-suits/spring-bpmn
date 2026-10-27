package de.nemeses.spring.bpmn.repository.jpa;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import de.nemeses.spring.bpmn.repository.NoSuchProcessDefinitionException;
import de.nemeses.spring.bpmn.repository.NoSuchProcessInstanceException;
import de.nemeses.spring.bpmn.repository.ProcessDefinition;
import de.nemeses.spring.bpmn.repository.ProcessDefinitionService;
import de.nemeses.spring.bpmn.repository.ProcessInstance;
import de.nemeses.spring.bpmn.repository.ProcessInstanceRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

/**
 *
 */
@SpringBootTest(classes = JpaProcessInstanceRepositoryTest.TestConfig.class,
        properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class JpaProcessInstanceRepositoryTest {

    @Autowired
    private ProcessDefinitionService definitions;

    @Autowired
    private ProcessInstanceRepository repository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void testCreateStoresInstance() throws NoSuchProcessDefinitionException {
        this.definitions.register(new ProcessDefinitionEntity("order", new Date(1000), "<v1/>"));
        this.definitions.register(new ProcessDefinitionEntity("order", new Date(2000), "<v2/>"));
        final ProcessDefinition definition = this.definitions.retrieveLatest("order");
        final Map<String, Object> variables = Map.of("amount", 3, "customer", "c1",
                "items", List.of("a", "b"), "address", Map.of("city", "Munich"));

        final ProcessInstance created = this.repository.create(definition, variables);

        Assertions.assertNotNull(created.id());
        Assertions.assertNotNull(created.startDate());
        Assertions.assertNull(created.endDate());

        final EntityManager entityManager = this.entityManagerFactory.createEntityManager();
        try {
            final ProcessInstance loaded = entityManager.find(ProcessInstanceEntity.class, created.id());
            Assertions.assertEquals(variables, loaded.variables());
            Assertions.assertEquals("<v2/>", loaded.processDefinition().bpmnXml());
            Assertions.assertEquals(created.startDate().getTime(), loaded.startDate().getTime());
            Assertions.assertNull(loaded.endDate());
        } finally {
            entityManager.close();
        }
    }

    @Test
    void testCreateAssignsUniqueIds() throws NoSuchProcessDefinitionException {
        this.definitions.register(new ProcessDefinitionEntity("unique", null, "<u/>"));
        final ProcessDefinition definition = this.definitions.retrieveLatest("unique");
        Assertions.assertNotEquals(this.repository.create(definition, Map.of()).id(),
                this.repository.create(definition, null).id());
    }

    @Test
    void testCreateRejectsUnknownDefinition() {
        final ProcessDefinition foreign = new ProcessDefinitionEntity("foreign", new Date(), "<f/>");
        Assertions.assertThrows(IllegalArgumentException.class, () -> this.repository.create(foreign, Map.of()));
    }

    @Test
    void testRetrieveReturnsStoredInstance() throws NoSuchProcessDefinitionException, NoSuchProcessInstanceException {
        this.definitions.register(new ProcessDefinitionEntity("retrieve", null, "<r/>"));
        final ProcessInstance created = this.repository.create(this.definitions.retrieveLatest("retrieve"),
                Map.of("amount", 5));

        final ProcessInstance retrieved = this.repository.retrieve(created.id());

        Assertions.assertEquals(created.id(), retrieved.id());
        Assertions.assertEquals(Map.of("amount", 5), retrieved.variables());
        Assertions.assertEquals("<r/>", retrieved.processDefinition().bpmnXml());
        Assertions.assertNull(retrieved.endDate());
    }

    @Test
    void testRetrieveUnknownInstanceFails() {
        Assertions.assertThrows(NoSuchProcessInstanceException.class, () -> this.repository.retrieve(UUID.randomUUID()));
    }

    @Test
    void testUpdateEndDateStoresEndDate() throws NoSuchProcessDefinitionException, NoSuchProcessInstanceException {
        this.definitions.register(new ProcessDefinitionEntity("end", null, "<e/>"));
        final ProcessInstance created = this.repository.create(this.definitions.retrieveLatest("end"), Map.of());
        final Date endDate = new Date(created.startDate().getTime() + 1000);

        Assertions.assertEquals(endDate.getTime(), this.repository.updateEndDate(created.id(), endDate).endDate().getTime());

        final EntityManager entityManager = this.entityManagerFactory.createEntityManager();
        try {
            Assertions.assertEquals(endDate.getTime(),
                    entityManager.find(ProcessInstanceEntity.class, created.id()).endDate().getTime());
        } finally {
            entityManager.close();
        }
        Assertions.assertEquals(endDate.getTime(), this.repository.retrieve(created.id()).endDate().getTime());
    }

    @Test
    void testUpdateVariablesReplacesVariables()
            throws NoSuchProcessDefinitionException, NoSuchProcessInstanceException {
        this.definitions.register(new ProcessDefinitionEntity("vars", null, "<v/>"));
        final ProcessInstance created = this.repository.create(this.definitions.retrieveLatest("vars"),
                Map.of("amount", 3, "customer", "c1"));
        final Map<String, Object> variables = Map.of("amount", 4, "items", List.of("a"));

        Assertions.assertEquals(variables, this.repository.updateVariables(created.id(), variables).variables());

        final EntityManager entityManager = this.entityManagerFactory.createEntityManager();
        try {
            Assertions.assertEquals(variables, entityManager.find(ProcessInstanceEntity.class, created.id()).variables());
        } finally {
            entityManager.close();
        }
        Assertions.assertEquals(variables, this.repository.retrieve(created.id()).variables());
    }

    @Test
    void testVariablesAreStoredAsOneRowEach() throws NoSuchProcessDefinitionException {
        this.definitions.register(new ProcessDefinitionEntity("rows", null, "<r/>"));
        final ProcessInstance created = this.repository.create(this.definitions.retrieveLatest("rows"),
                Map.of("amount", 3, "address", Map.of("city", "Munich")));

        Assertions.assertEquals(Map.of("amount", "3", "address", "{\"city\":\"Munich\"}"), this.rows(created.id()));
    }

    @Test
    void testUpdateVariablesChangesRowsPerVariable()
            throws NoSuchProcessDefinitionException, NoSuchProcessInstanceException {
        this.definitions.register(new ProcessDefinitionEntity("rowupdate", null, "<u/>"));
        final ProcessInstance created = this.repository.create(this.definitions.retrieveLatest("rowupdate"),
                Map.of("kept", "k", "changed", 1, "removed", true));

        this.repository.updateVariables(created.id(), Map.of("kept", "k", "changed", 2, "added", List.of(1)));

        Assertions.assertEquals(Map.of("kept", "\"k\"", "changed", "2", "added", "[1]"), this.rows(created.id()));

        this.repository.updateVariables(created.id(), Map.of());

        Assertions.assertEquals(Map.of(), this.rows(created.id()));
        Assertions.assertEquals(Map.of(), this.repository.retrieve(created.id()).variables());
    }

    private Map<String, String> rows(final UUID instanceId) {
        final EntityManager entityManager = this.entityManagerFactory.createEntityManager();
        try {
            final Map<String, String> rows = new HashMap<>();
            for (final Object row : entityManager.createNativeQuery(
                    "select variable_name, cast(variable_value as varchar(1000)) from spring_bpmn_process_variable"
                            + " where process_instance_id = ?1")
                    .setParameter(1, instanceId).getResultList()) {
                final Object[] columns = (Object[]) row;
                rows.put((String) columns[0], String.valueOf(columns[1]));
            }
            return rows;
        } finally {
            entityManager.close();
        }
    }

    @Test
    void testVariablePrimaryKeyIsInstanceAndName() {
        final EntityManager entityManager = this.entityManagerFactory.createEntityManager();
        try {
            final List<?> columns = entityManager.createNativeQuery("select lower(k.column_name)"
                    + " from information_schema.table_constraints c join information_schema.key_column_usage k"
                    + " on k.constraint_name = c.constraint_name and k.table_name = c.table_name"
                    + " where c.constraint_type = 'PRIMARY KEY'"
                    + " and lower(c.table_name) = 'spring_bpmn_process_variable' order by k.column_name")
                    .getResultList();
            Assertions.assertEquals(List.of("process_instance_id", "variable_name"), columns);
        } finally {
            entityManager.close();
        }
    }

    @Test
    void testUpdateVariablesOfUnknownInstanceFails() {
        Assertions.assertThrows(NoSuchProcessInstanceException.class,
                () -> this.repository.updateVariables(UUID.randomUUID(), Map.of()));
    }

    @Test
    void testUpdateEndDateOfUnknownInstanceFails() {
        Assertions.assertThrows(NoSuchProcessInstanceException.class,
                () -> this.repository.updateEndDate(UUID.randomUUID(), new Date()));
    }

    @Test
    void testLockRequiresTransaction() throws NoSuchProcessDefinitionException {
        this.definitions.register(new ProcessDefinitionEntity("lockTx", null, "<l/>"));
        final ProcessInstance created = this.repository.create(this.definitions.retrieveLatest("lockTx"), Map.of());
        Assertions.assertThrows(IllegalTransactionStateException.class, () -> this.repository.lock(created.id()));
        final IllegalStateException unknown = Assertions.assertThrows(IllegalStateException.class,
                () -> new TransactionTemplate(this.transactionManager).executeWithoutResult(status -> {
                    try {
                        this.repository.lock(UUID.randomUUID());
                    } catch (final NoSuchProcessInstanceException e) {
                        throw new IllegalStateException(e);
                    }
                }));
        Assertions.assertInstanceOf(NoSuchProcessInstanceException.class, unknown.getCause());
    }

    @Test
    void testLockSerializesTransactions() throws Exception {
        this.definitions.register(new ProcessDefinitionEntity("lock", null, "<l/>"));
        final UUID id = this.repository.create(this.definitions.retrieveLatest("lock"), Map.of("a", 1, "b", 2)).id();
        final TransactionTemplate transaction = new TransactionTemplate(this.transactionManager);
        final CountDownLatch locked = new CountDownLatch(1);
        final AtomicBoolean released = new AtomicBoolean();
        final Date endDate = new Date();

        final ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            final Future<?> first = executor.submit(() -> transaction.executeWithoutResult(status -> {
                try {
                    this.repository.lock(id);
                    locked.countDown();
                    Thread.sleep(300);
                    this.repository.updateEndDate(id, endDate);
                    this.repository.updateVariables(id, Map.of("a", 10, "c", 3));
                    released.set(true);
                } catch (final NoSuchProcessInstanceException | InterruptedException e) {
                    throw new IllegalStateException(e);
                }
            }));
            Assertions.assertTrue(locked.await(10, TimeUnit.SECONDS));

            final ProcessInstance second = transaction.execute(status -> {
                try {
                    // loads the instance and its variables into the persistence context before the lock
                    this.repository.retrieve(id);
                    return this.repository.lock(id);
                } catch (final NoSuchProcessInstanceException e) {
                    throw new IllegalStateException(e);
                }
            });
            // only got the lock after the first transaction committed, and sees its changes
            Assertions.assertTrue(released.get());
            Assertions.assertEquals(endDate.getTime(), second.endDate().getTime());
            // the lock reloads the eagerly loaded variable rows as well
            Assertions.assertEquals(Map.of("a", 10, "c", 3), second.variables());
            first.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
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
