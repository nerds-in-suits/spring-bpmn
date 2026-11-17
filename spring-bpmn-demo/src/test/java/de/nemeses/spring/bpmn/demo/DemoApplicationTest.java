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
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;

/**
 * The demo application starts with all modules, serves the UI and the REST API, registers the sample processes and
 * runs them end to end, secured by the demo users (requests as {@code admin} with HTTP Basic). Uses an in-memory
 * database instead of the file database of {@code application.yaml}.
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
        this.mvc = MockMvcBuilders.webAppContextSetup(this.context).apply(SecurityMockMvcConfigurers.springSecurity())
                .defaultRequest(MockMvcRequestBuilders.get("/").with(
                        SecurityMockMvcRequestPostProcessors.httpBasic("admin", "admin")))
                .build();
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
    void testNeedsLogin() throws Exception {
        final MockMvc anonymous = this.withoutUser();
        anonymous.perform(MockMvcRequestBuilders.get("/bpmn-ui/").accept(MediaType.TEXT_HTML))
                .andExpect(MockMvcResultMatchers.status().is3xxRedirection())
                .andExpect(MockMvcResultMatchers.redirectedUrl("/login"));
        anonymous.perform(MockMvcRequestBuilders.get(BASE + "/process-definitions"))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());
    }

    @Test
    void testViewerMayOnlyRead() throws Exception {
        final MockMvc viewer = this.withoutUser();
        viewer.perform(MockMvcRequestBuilders.get(BASE + "/process-definitions")
                .with(SecurityMockMvcRequestPostProcessors.httpBasic("viewer", "viewer")))
                .andExpect(MockMvcResultMatchers.status().isOk());
        viewer.perform(MockMvcRequestBuilders.post(BASE + "/process-instances")
                .with(SecurityMockMvcRequestPostProcessors.httpBasic("viewer", "viewer"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"processDefinition\":\"helloWorld\"}"))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
    }

    @Test
    void testRegistersSampleProcesses() throws Exception {
        this.mvc.perform(MockMvcRequestBuilders.get(BASE + "/process-definitions"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$[*].name").value(
                        Matchers.containsInAnyOrder("claimHandling", "helloWorld", "orderApproval")));
    }

    @Test
    void testClaimHandlingHasDiagram() throws Exception {
        this.mvc.perform(MockMvcRequestBuilders.get(BASE + "/process-definitions/claimHandling/bpmn"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().string(Matchers.containsString("bpmndi:BPMNDiagram")));
    }

    @Test
    void testSmallClaimIsPaidOutAutomatically() throws Exception {
        final String id = this.start("{\"processDefinition\":\"claimHandling\",\"variables\":"
                + "{\"amount\":300,\"documents\":[\"invoice\",\"photo\"]}}");
        this.awaitFinished(id);
        this.mvc.perform(MockMvcRequestBuilders.get(BASE + "/process-instances/" + id))
                .andExpect(MockMvcResultMatchers.jsonPath("$.variables.expertReviewRequired").value(false))
                .andExpect(MockMvcResultMatchers.jsonPath("$.variables.checkedDocuments").value(
                        Matchers.contains("invoice: checked", "photo: checked")))
                .andExpect(MockMvcResultMatchers.jsonPath("$.variables.decision").value("approved"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.variables.payout").value(200))
                .andExpect(MockMvcResultMatchers.jsonPath("$.variables.customerNotified").value(true));
    }

    @Test
    void testUncoveredClaimIsRejected() throws Exception {
        final String id = this.start("{\"processDefinition\":\"claimHandling\",\"variables\":"
                + "{\"amount\":5000,\"policyActive\":false}}");
        this.awaitFinished(id);
        this.mvc.perform(MockMvcRequestBuilders.get(BASE + "/process-instances/" + id))
                .andExpect(MockMvcResultMatchers.jsonPath("$.variables.decision").value("rejected"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.variables.payout").value(0))
                .andExpect(MockMvcResultMatchers.jsonPath("$.variables.customerNotified").value(true));
    }

    @Test
    void testLargeClaimRejectedByExpert() throws Exception {
        final String id = this.start("{\"processDefinition\":\"claimHandling\",\"variables\":"
                + "{\"amount\":5000,\"documents\":[\"report\"]}}");
        final String taskId = this.awaitUserTask(id, "Review claim");
        this.mvc.perform(MockMvcRequestBuilders.post(BASE + "/user-tasks/" + taskId + "/complete")
                .contentType(MediaType.APPLICATION_JSON).content("{\"approved\":false}"))
                .andExpect(MockMvcResultMatchers.status().isNoContent());
        this.awaitFinished(id);
        this.mvc.perform(MockMvcRequestBuilders.get(BASE + "/process-instances/" + id))
                .andExpect(MockMvcResultMatchers.jsonPath("$.variables.checkedDocuments").value(
                        Matchers.contains("report: checked")))
                .andExpect(MockMvcResultMatchers.jsonPath("$.variables.decision").value("rejected"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.variables.payout").value(0));
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
        final String taskId = this.awaitUserTask(id, "Approve order");
        this.mvc.perform(MockMvcRequestBuilders.post(BASE + "/user-tasks/" + taskId + "/complete")
                .contentType(MediaType.APPLICATION_JSON).content("{\"approved\":true}"))
                .andExpect(MockMvcResultMatchers.status().isNoContent());
        this.awaitFinished(id);
    }

    private String awaitUserTask(final String id, final String nodeName) throws Exception {
        Awaitility.await().atMost(Duration.ofSeconds(20)).until(() -> JsonPath.<List<?>>read(this.mvc
                .perform(MockMvcRequestBuilders.get(BASE + "/user-tasks").param("processInstance", id))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8), "$").size() == 1);
        final String tasks = this.mvc.perform(MockMvcRequestBuilders.get(BASE + "/user-tasks")
                .param("processInstance", id))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].nodeName").value(nodeName))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(tasks, "$[0].id");
    }

    private MockMvc withoutUser() {
        return MockMvcBuilders.webAppContextSetup(this.context).apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
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
