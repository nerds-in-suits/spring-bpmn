package de.nemeses.spring.bpmn.demo;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import org.awaitility.Awaitility;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;

/**
 * The demo application starts with all modules, serves the UI and the REST API, registers the sample processes and
 * runs them end to end. Uses an in-memory database instead of the file database of {@code application.yaml}.
 */
@SpringBootTest(classes = DemoApplication.class, properties = {
    "spring.datasource.url=jdbc:h2:mem:spring-bpmn-demo-test", "spring.jpa.hibernate.ddl-auto=create-drop" })
class DemoApplicationTest {

    private static final String BASE = "/api/bpmn/v1";

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        this.mvc = MockMvcBuilders.webAppContextSetup(this.context).build();
    }

    @Test
    void testServesUi() throws Exception {
        this.mvc.perform(MockMvcRequestBuilders.get("/bpmn-ui/"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.forwardedUrl("/bpmn-ui/index.html"));
        this.mvc.perform(MockMvcRequestBuilders.get("/bpmn-ui/config.json"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.apiBasePath").value(BASE));
    }

    @Test
    void testRegistersSampleProcesses() throws Exception {
        this.mvc.perform(MockMvcRequestBuilders.get(BASE + "/process-definitions"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$[*].name").value(
                        Matchers.containsInAnyOrder("helloWorld", "orderApproval")));
    }

    @Test
    void testHelloWorldRunsToEnd() throws Exception {
        final String id = this.start("{\"processDefinition\":\"helloWorld\",\"variables\":{\"name\":\"Demo\"}}");
        this.awaitFinished(id);
        this.mvc.perform(MockMvcRequestBuilders.get(BASE + "/process-instances/" + id))
                .andExpect(MockMvcResultMatchers.jsonPath("$.variables.greeting").value("Hello, Demo!"));
    }

    @Test
    void testSmallOrderIsApprovedAutomatically() throws Exception {
        final String id = this.start("{\"processDefinition\":\"orderApproval\",\"variables\":{\"amount\":50}}");
        this.awaitFinished(id);
        this.mvc.perform(MockMvcRequestBuilders.get(BASE + "/process-instances/" + id))
                .andExpect(MockMvcResultMatchers.jsonPath("$.variables.approvalRequired").value(false))
                .andExpect(MockMvcResultMatchers.jsonPath("$.variables.approved").value(true));
    }

    @Test
    void testLargeOrderWaitsForApproval() throws Exception {
        final String id = this.start("{\"processDefinition\":\"orderApproval\",\"variables\":{\"amount\":500}}");
        Awaitility.await().atMost(Duration.ofSeconds(20)).until(() -> JsonPath.<List<?>>read(this.mvc
                .perform(MockMvcRequestBuilders.get(BASE + "/user-tasks").param("processInstance", id))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8), "$").size() == 1);
        final String tasks = this.mvc.perform(MockMvcRequestBuilders.get(BASE + "/user-tasks")
                .param("processInstance", id))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].nodeName").value("Approve order"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        this.mvc.perform(MockMvcRequestBuilders.post(BASE + "/user-tasks/" + JsonPath.read(tasks, "$[0].id")
                + "/complete").contentType(MediaType.APPLICATION_JSON).content("{\"approved\":true}"))
                .andExpect(MockMvcResultMatchers.status().isNoContent());
        this.awaitFinished(id);
    }

    private String start(final String request) throws Exception {
        final String started = this.mvc.perform(MockMvcRequestBuilders.post(BASE + "/process-instances")
                .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(started, "$.id");
    }

    private void awaitFinished(final String id) {
        Awaitility.await().atMost(Duration.ofSeconds(20)).until(() -> "finished".equals(JsonPath.read(this.mvc
                .perform(MockMvcRequestBuilders.get(BASE + "/process-instances/" + id))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8), "$.state")));
    }
}
