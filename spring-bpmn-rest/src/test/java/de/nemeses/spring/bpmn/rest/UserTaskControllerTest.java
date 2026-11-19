package de.nemeses.spring.bpmn.rest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Assertions;
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

import de.nemeses.spring.bpmn.exec.ProcessEngineService;
import de.nemeses.spring.bpmn.repository.ProcessInstance;
import de.nemeses.spring.bpmn.repository.ProcessStep;

/**
 * {@link UserTaskController} on the real engine and repositories.
 */
@SpringBootTest(classes = TestEngineConfiguration.class)
class UserTaskControllerTest {

    private static final String BASE = "/api/bpmn/v1/user-tasks";

    private static final String COMPLETE = "/complete";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ProcessEngineService engine;

    @Autowired
    private TestProcesses processes;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        this.mvc = MockMvcBuilders.webAppContextSetup(this.context).build();
    }

    /**
     * @return an instance waiting in user task {@code approve}
     */
    private ProcessInstance waiting() throws Exception {
        return this.processes.waiting(this.processes.register("order", TestProcesses::order),
                Map.of("amount", 3));
    }

    /**
     * Two open user tasks with different start dates: {@code approve} opens at the start, {@code review} only after
     * {@code check} was completed.
     */
    @Test
    void testList() throws Exception {
        final String name = this.processes.register("review", id -> TestProcesses.document(id,
                "<startEvent id=\"start\"/><parallelGateway id=\"fork\"/>"
                + "<userTask id=\"approve\" name=\"Approve order\"/><userTask id=\"check\" name=\"Check order\"/>"
                + "<userTask id=\"review\" name=\"Review order\"/><endEvent id=\"end1\"/><endEvent id=\"end2\"/>"
                + "<sequenceFlow id=\"f1\" sourceRef=\"start\" targetRef=\"fork\"/>"
                + "<sequenceFlow id=\"f2\" sourceRef=\"fork\" targetRef=\"approve\"/>"
                + "<sequenceFlow id=\"f3\" sourceRef=\"fork\" targetRef=\"check\"/>"
                + "<sequenceFlow id=\"f4\" sourceRef=\"check\" targetRef=\"review\"/>"
                + "<sequenceFlow id=\"f5\" sourceRef=\"approve\" targetRef=\"end1\"/>"
                + "<sequenceFlow id=\"f6\" sourceRef=\"review\" targetRef=\"end2\"/>"));
        final UUID id = this.engine.startProcess(name, Map.of()).id();
        final List<ProcessStep> opened = this.processes.userTasks(id, 2);
        final ProcessStep earlier = opened.stream().filter(step -> "approve".equals(step.nodeId())).findFirst()
                .orElseThrow();
        final ProcessStep check = opened.stream().filter(step -> "check".equals(step.nodeId())).findFirst()
                .orElseThrow();
        TestProcesses.after(earlier.startDate());
        this.engine.completeUserTask(check.id(), Map.of());
        final ProcessStep later = this.processes.userTasks(id, 2).stream()
                .filter(step -> "review".equals(step.nodeId())).findFirst().orElseThrow();

        this.mvc.perform(MockMvcRequestBuilders.get(BASE).param("processInstance", id.toString()))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(2))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].id").value(earlier.id().toString()))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].processInstanceId").value(id.toString()))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].nodeId").value("approve"))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].nodeName").value("Approve order"))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].startDate")
                        .value(Dates.instant(earlier.startDate()).toString()))
                .andExpect(MockMvcResultMatchers.jsonPath("$[1].id").value(later.id().toString()))
                .andExpect(MockMvcResultMatchers.jsonPath("$[1].nodeName").value("Review order"));
    }

    @Test
    void testListOfUnknownInstanceIsNotFound() throws Exception {
        final UUID id = UUID.randomUUID();

        this.mvc.perform(MockMvcRequestBuilders.get(BASE).param("processInstance", id.toString()))
                .andExpect(MockMvcResultMatchers.status().isNotFound())
                .andExpect(MockMvcResultMatchers.jsonPath("$.detail")
                        .value("No process instance found with id '" + id + "'"));
    }

    @Test
    void testListWithoutInstanceIsBadRequest() throws Exception {
        this.mvc.perform(MockMvcRequestBuilders.get(BASE))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void testComplete() throws Exception {
        final ProcessInstance instance = this.waiting();
        final UUID task = this.processes.userTasks(instance.id(), 1).getFirst().id();

        this.mvc.perform(MockMvcRequestBuilders.post(BASE + "/" + task + COMPLETE)
                .contentType(MediaType.APPLICATION_JSON).content("{\"approved\":true}"))
                .andExpect(MockMvcResultMatchers.status().isNoContent());

        Assertions.assertTrue(this.engine.openUserTasks(instance.id()).isEmpty());
        Assertions.assertEquals(Map.of("amount", 3, "approved", true),
                this.processes.ended(instance.id()).variables());
    }

    @Test
    void testCompleteWithoutBody() throws Exception {
        final ProcessInstance instance = this.waiting();
        final UUID task = this.processes.userTasks(instance.id(), 1).getFirst().id();

        this.mvc.perform(MockMvcRequestBuilders.post(BASE + "/" + task + COMPLETE))
                .andExpect(MockMvcResultMatchers.status().isNoContent());

        Assertions.assertTrue(this.engine.openUserTasks(instance.id()).isEmpty());
        Assertions.assertEquals(Map.of("amount", 3), this.processes.ended(instance.id()).variables());
    }

    @Test
    void testCompleteUnknownIsNotFound() throws Exception {
        this.mvc.perform(MockMvcRequestBuilders.post(BASE + "/" + UUID.randomUUID() + COMPLETE))
                .andExpect(MockMvcResultMatchers.status().isNotFound());
    }

    @Test
    void testCompleteCompletedIsUnprocessable() throws Exception {
        final ProcessInstance instance = this.waiting();
        final UUID task = this.processes.userTasks(instance.id(), 1).getFirst().id();
        this.engine.completeUserTask(task, Map.of());

        this.mvc.perform(MockMvcRequestBuilders.post(BASE + "/" + task + COMPLETE))
                .andExpect(MockMvcResultMatchers.status().isUnprocessableContent())
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }
}
