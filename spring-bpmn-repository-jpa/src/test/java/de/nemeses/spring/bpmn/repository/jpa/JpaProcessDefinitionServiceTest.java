package de.nemeses.spring.bpmn.repository.jpa;

import java.util.Collection;
import java.util.Date;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;

import de.nemeses.spring.bpmn.repository.ProcessDefinition;
import de.nemeses.spring.bpmn.repository.ProcessDefinitionService;

/**
 *
 */
@SpringBootTest(classes = JpaProcessDefinitionServiceTest.TestConfig.class,
        properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class JpaProcessDefinitionServiceTest {

    @Autowired
    private ProcessDefinitionService service;

    @Test
    void testRegisterAndRetrieve() {
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
        Assertions.assertNull(this.service.retrieveLatest("missing"));
        Assertions.assertTrue(this.service.retrieveAll("missing").isEmpty());
    }

    /**
     * Test application.
     */
    @Configuration
    @EnableAutoConfiguration
    static class TestConfig {
    }
}
