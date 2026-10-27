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
    void testUuidGeneratedOnPersist() {
        this.service.register(new ProcessDefinitionEntity("uuid", null, "<u1/>"));
        this.service.register(new ProcessDefinitionEntity("uuid", null, "<u2/>"));
        final List<UUID> ids = this.entityManager
                .createQuery("select d.id from ProcessDefinitionEntity d where d.processDefinitionName = 'uuid'",
                        UUID.class)
                .getResultList();
        Assertions.assertEquals(2, ids.size());
        Assertions.assertNotNull(ids.get(0));
        Assertions.assertNotNull(ids.get(1));
        Assertions.assertNotEquals(ids.get(0), ids.get(1));
    }

    /**
     * Test application.
     */
    @Configuration
    @EnableAutoConfiguration(excludeName = "de.nemeses.spring.bpmn.ProcessDefinitionAutoConfiguration")
    static class TestConfig {
    }
}
