package de.nemeses.spring.bpmn.repository.jpa;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;

import de.nemeses.spring.bpmn.repository.NoSuchProcessDefinitionException;
import de.nemeses.spring.bpmn.repository.ProcessDefinition;
import de.nemeses.spring.bpmn.repository.ProcessDefinitionService;
import jakarta.persistence.EntityManager;

/**
 *
 */
@SpringBootTest(classes = JpaProcessDefinitionServiceTest.TestConfig.class,
        properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class JpaProcessDefinitionServiceTest {

    @Autowired
    private ProcessDefinitionService service;

    @Autowired
    private EntityManager entityManager;

    @Test
    void testRegisterAndRetrieve() throws NoSuchProcessDefinitionException {
        this.service.register(new ProcessDefinitionEntity("order", new Date(1000), "<v1/>"));
        this.service.register(new ProcessDefinitionEntity("order", new Date(3000), "<v3/>"));
        this.service.register(new ProcessDefinitionEntity("order", new Date(2000), "<v2/>"));
        this.service.register(new ProcessDefinitionEntity("other", new Date(5000), "<o/>"));

        Assertions.assertEquals("<v3/>", this.service.retrieveLatest("order").bpmnXml());
        final Collection<ProcessDefinition> all = this.service.retrieveAll("order");
        Assertions.assertEquals(3, all.size());
        Assertions.assertEquals("<v3/>", all.iterator().next().bpmnXml());
    }

    @Test
    void testUnknown() {
        Assertions.assertThrows(NoSuchProcessDefinitionException.class,
                () -> this.service.retrieveLatest("missing"));
        Assertions.assertTrue(this.service.retrieveAll("missing").isEmpty());
    }

    @Test
    void testCreatedDateInitialized() throws NoSuchProcessDefinitionException {
        this.service.register(new ProcessDefinitionEntity("dated", null, "<d/>"));
        Assertions.assertNotNull(this.service.retrieveLatest("dated").createdDate());
    }

    @Test
    void testDuplicateNameAndCreatedRejected() {
        this.service.register(new ProcessDefinitionEntity("duplicate", new Date(7000), "<a/>"));
        Assertions.assertThrows(DataIntegrityViolationException.class,
                () -> this.service.register(new ProcessDefinitionEntity("duplicate", new Date(7000), "<b/>")));
        Assertions.assertEquals(1, this.service.retrieveAll("duplicate").size());
    }

    @Test
    void testUuidGeneratedOnPersist() {
        this.service.register(new ProcessDefinitionEntity("uuid", new Date(8000), "<u1/>"));
        this.service.register(new ProcessDefinitionEntity("uuid", new Date(9000), "<u2/>"));
        final List<UUID> ids = this.entityManager
                .createQuery("select d.id from ProcessDefinitionEntity d where d.processDefinitionName = 'uuid'",
                        UUID.class)
                .getResultList();
        Assertions.assertEquals(2, ids.size());
        Assertions.assertNotNull(ids.get(0));
        Assertions.assertNotNull(ids.get(1));
        Assertions.assertNotEquals(ids.get(0), ids.get(1));
    }

    @Test
    void testRetrieveLatestAll() {
        this.service.register(new ProcessDefinitionEntity("latest-b", new Date(500), "<b/>"));
        this.service.register(new ProcessDefinitionEntity("latest-a", new Date(2000), "<a2/>"));
        this.service.register(new ProcessDefinitionEntity("latest-a", new Date(1000), "<a1/>"));

        final List<ProcessDefinition> all = this.service.retrieveLatestAll();

        final List<String> names = all.stream().map(ProcessDefinition::name).toList();
        Assertions.assertEquals(names.stream().distinct().sorted().toList(), names);
        Assertions.assertEquals(List.of("<a2/>", "<b/>"), all.stream()
                .filter(definition -> definition.name().startsWith("latest-"))
                .map(ProcessDefinition::bpmnXml)
                .toList());
    }

    /**
     * Test application.
     */
    @Configuration
    @EnableAutoConfiguration(excludeName = "de.nemeses.spring.bpmn.ProcessDefinitionAutoConfiguration")
    static class TestConfig {
    }
}
