package de.nemeses.spring.bpmn.repository.jpa;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;

import de.nemeses.spring.bpmn.repository.NoSuchProcessDefinitionException;
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

    /**
     * Test application.
     */
    @Configuration
    @EnableAutoConfiguration(excludeName = "de.nemeses.spring.bpmn.ProcessDefinitionAutoConfiguration")
    static class TestConfig {
    }
}
