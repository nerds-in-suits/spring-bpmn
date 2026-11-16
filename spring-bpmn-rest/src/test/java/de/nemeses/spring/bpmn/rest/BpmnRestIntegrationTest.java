package de.nemeses.spring.bpmn.rest;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;

import de.nemeses.spring.tasks.TaskConfiguration;

/**
 * The REST API on the real engine with the JPA repositories and the JPA task persistence on H2: deploys a process,
 * starts an instance, completes its user task and reads the result.
 */
@SpringBootTest(classes = BpmnRestIntegrationTest.TestApplication.class, properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.bpmn.rest.deployment-enabled=true",
    "spring.tasks.pool-size=2" })
class BpmnRestIntegrationTest {

    private static final String BASE = "/api/bpmn/v1";

    private static final String APPROVAL = "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\""
            + " id=\"d\" targetNamespace=\"urn:t\"><process id=\"approval\"><startEvent id=\"start\"/>"
            + "<userTask id=\"approve\" name=\"Approve\"/><endEvent id=\"end\"/>"
            + "<sequenceFlow id=\"f1\" sourceRef=\"start\" targetRef=\"approve\"/>"
            + "<sequenceFlow id=\"f2\" sourceRef=\"approve\" targetRef=\"end\"/></process></definitions>";

    @Autowired
    private WebApplicationContext context;

    @Test
    void testDeployStartCompleteAndRead() throws Exception {
        final MockMvc mvc = MockMvcBuilders.webAppContextSetup(this.context).build();

        mvc.perform(MockMvcRequestBuilders.post(BASE + "/process-definitions").contentType(MediaType.APPLICATION_XML)
                .content(APPROVAL))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].name").value("approval"))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].registered").value(true));
        mvc.perform(MockMvcRequestBuilders.post(BASE + "/process-definitions").contentType(MediaType.APPLICATION_XML)
                .content(APPROVAL))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].registered").value(false));

        final String started = mvc.perform(MockMvcRequestBuilders.post(BASE + "/process-instances")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"processDefinition\":\"approval\",\"variables\":{\"amount\":3}}"))
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        final String id = JsonPath.read(started, "$.id");

        final String instance = BASE + "/process-instances/" + id;
        Awaitility.await().atMost(Duration.ofSeconds(20)).until(() -> JsonPath.<List<?>>read(mvc
                .perform(MockMvcRequestBuilders.get(BASE + "/user-tasks").param("processInstance", id))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8), "$").size() == 1);
        final String tasks = mvc.perform(MockMvcRequestBuilders.get(BASE + "/user-tasks").param("processInstance", id))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].nodeName").value("Approve"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        mvc.perform(MockMvcRequestBuilders.get(BASE + "/process-definitions"))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].runningInstances").value(1))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].finishedInstances").value(0));

        mvc.perform(MockMvcRequestBuilders.post(BASE + "/user-tasks/" + JsonPath.read(tasks, "$[0].id") + "/complete")
                .contentType(MediaType.APPLICATION_JSON).content("{\"approved\":true}"))
                .andExpect(MockMvcResultMatchers.status().isNoContent());

        Awaitility.await().atMost(Duration.ofSeconds(20)).until(() -> "finished".equals(JsonPath.read(mvc
                .perform(MockMvcRequestBuilders.get(instance))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8), "$.state")));
        mvc.perform(MockMvcRequestBuilders.get(instance))
                .andExpect(MockMvcResultMatchers.jsonPath("$.variables.amount").value(3))
                .andExpect(MockMvcResultMatchers.jsonPath("$.variables.approved").value(true));
        final String steps = mvc.perform(MockMvcRequestBuilders.get(instance + "/steps"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Assertions.assertEquals(List.of("startEvent", "userTask", "endEvent"), JsonPath.read(steps, "$[*].type"));
        Assertions.assertEquals(List.of("finished", "finished", "finished"), JsonPath.read(steps, "$[*].state"));
        mvc.perform(MockMvcRequestBuilders.get(BASE + "/process-instances").param("definition", "approval")
                .param("state", "finished"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.total").value(1))
                .andExpect(MockMvcResultMatchers.jsonPath("$.items[0].id").value(id));
        mvc.perform(MockMvcRequestBuilders.get(BASE + "/process-definitions"))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].runningInstances").value(0))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].finishedInstances").value(1));
        mvc.perform(MockMvcRequestBuilders.get(BASE + "/user-tasks").param("processInstance", id))
                .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(0));
    }

    /**
     * Test application: all auto-configurations plus the task service.
     */
    @Configuration
    @EnableAutoConfiguration
    @Import(TaskConfiguration.class)
    static class TestApplication {
    }
}
