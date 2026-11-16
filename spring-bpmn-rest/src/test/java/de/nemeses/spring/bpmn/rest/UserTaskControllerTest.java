package de.nemeses.spring.bpmn.rest;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import de.nemeses.spring.bpmn.exec.ProcessEngineException;
import de.nemeses.spring.bpmn.exec.ProcessEngineService;
import de.nemeses.spring.bpmn.repository.NoSuchProcessInstanceException;
import de.nemeses.spring.bpmn.repository.NoSuchProcessStepException;
import de.nemeses.spring.bpmn.repository.ProcessInstanceRepository;
import de.nemeses.spring.bpmn.repository.ProcessStep;

/**
 * {@link UserTaskController} on a mocked engine.
 */
@SpringBootTest(classes = MockedEngineConfiguration.class)
class UserTaskControllerTest {

    private static final String BASE = "/api/bpmn/v1/user-tasks";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ProcessEngineService engine;

    @Autowired
    private ProcessInstanceRepository instances;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        this.mvc = MockMvcBuilders.webAppContextSetup(this.context).build();
    }

    @AfterEach
    void tearDown() {
        Mockito.reset(this.engine, this.instances);
    }

    @Test
    void testList() throws Exception {
        final UUID id = UUID.randomUUID();
        final ProcessStep later = new MockedEngineConfiguration.Step(id, "approve", 3000, null);
        final ProcessStep earlier = new MockedEngineConfiguration.Step(id, "approve", 2000, null);
        Mockito.when(this.engine.openUserTasks(id)).thenReturn(List.of(later, earlier));
        Mockito.when(this.instances.retrieve(id)).thenReturn(new MockedEngineConfiguration.Instance(
                id,
                new MockedEngineConfiguration.Definition("order", new Date(1000), MockedEngineConfiguration.ORDER_XML),
                Map.of(),
                new Date(1000),
                null,
                null,
                null));

        this.mvc.perform(MockMvcRequestBuilders.get(BASE).param("processInstance", id.toString()))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(2))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].id").value(earlier.id().toString()))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].processInstanceId").value(id.toString()))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].nodeId").value("approve"))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].nodeName").value("Approve order"))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].startDate").value("1970-01-01T00:00:02Z"))
                .andExpect(MockMvcResultMatchers.jsonPath("$[1].id").value(later.id().toString()));
    }

    @Test
    void testListOfUnknownInstanceIsNotFound() throws Exception {
        final UUID id = UUID.randomUUID();
        Mockito.when(this.engine.openUserTasks(id)).thenThrow(new ProcessEngineException("Cannot list.",
                new NoSuchProcessInstanceException(id)));

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
        final UUID id = UUID.randomUUID();

        this.mvc.perform(MockMvcRequestBuilders.post(BASE + "/" + id + "/complete")
                .contentType(MediaType.APPLICATION_JSON).content("{\"approved\":true}"))
                .andExpect(MockMvcResultMatchers.status().isNoContent());

        Mockito.verify(this.engine).completeUserTask(id, Map.of("approved", true));
    }

    @Test
    void testCompleteWithoutBody() throws Exception {
        final UUID id = UUID.randomUUID();

        this.mvc.perform(MockMvcRequestBuilders.post(BASE + "/" + id + "/complete"))
                .andExpect(MockMvcResultMatchers.status().isNoContent());

        Mockito.verify(this.engine).completeUserTask(id, null);
    }

    @Test
    void testCompleteUnknownIsNotFound() throws Exception {
        final UUID id = UUID.randomUUID();
        Mockito.doThrow(new ProcessEngineException("Cannot execute.", new NoSuchProcessStepException(id)))
                .when(this.engine).completeUserTask(ArgumentMatchers.eq(id), ArgumentMatchers.any());

        this.mvc.perform(MockMvcRequestBuilders.post(BASE + "/" + id + "/complete"))
                .andExpect(MockMvcResultMatchers.status().isNotFound());
    }

    @Test
    void testCompleteCompletedIsUnprocessable() throws Exception {
        final UUID id = UUID.randomUUID();
        Mockito.doThrow(new ProcessEngineException("Already completed."))
                .when(this.engine).completeUserTask(ArgumentMatchers.eq(id), ArgumentMatchers.any());

        this.mvc.perform(MockMvcRequestBuilders.post(BASE + "/" + id + "/complete"))
                .andExpect(MockMvcResultMatchers.status().isUnprocessableContent())
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }
}
