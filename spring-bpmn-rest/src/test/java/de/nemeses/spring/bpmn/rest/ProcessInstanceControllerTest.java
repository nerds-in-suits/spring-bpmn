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
import de.nemeses.spring.bpmn.repository.NoSuchProcessDefinitionException;
import de.nemeses.spring.bpmn.repository.NoSuchProcessInstanceException;
import de.nemeses.spring.bpmn.repository.ProcessDefinition;
import de.nemeses.spring.bpmn.repository.ProcessInstance;
import de.nemeses.spring.bpmn.repository.ProcessInstancePage;
import de.nemeses.spring.bpmn.repository.ProcessInstanceRepository;
import de.nemeses.spring.bpmn.repository.ProcessInstanceSort;
import de.nemeses.spring.bpmn.repository.ProcessStep;
import de.nemeses.spring.bpmn.repository.ProcessStepRepository;

/**
 * {@link ProcessInstanceController} on a mocked engine and repositories.
 */
@SpringBootTest(classes = MockedEngineConfiguration.class)
class ProcessInstanceControllerTest {

    private static final String BASE = "/api/bpmn/v1/process-instances";

    private static final ProcessDefinition ORDER = new MockedEngineConfiguration.Definition(
            "order",
            new Date(1000),
            MockedEngineConfiguration.ORDER_XML);

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ProcessEngineService engine;

    @Autowired
    private ProcessInstanceRepository instances;

    @Autowired
    private ProcessStepRepository steps;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        this.mvc = MockMvcBuilders.webAppContextSetup(this.context).build();
    }

    @AfterEach
    void tearDown() {
        Mockito.reset(this.engine, this.instances, this.steps);
    }

    private static ProcessInstance instance(final UUID id, final Long end) {
        return new MockedEngineConfiguration.Instance(
                id,
                ORDER,
                Map.of("amount", 3),
                new Date(2000),
                end == null ? null : new Date(end),
                null,
                null);
    }

    @Test
    void testListWithDefaults() throws Exception {
        final UUID id = UUID.randomUUID();
        Mockito.when(this.instances.query(null, null, ProcessInstanceSort.START_DATE, true, 0, 20))
                .thenReturn(new ProcessInstancePage(List.of(ProcessInstanceControllerTest.instance(id, null)), 1));

        this.mvc.perform(MockMvcRequestBuilders.get(BASE))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.page").value(0))
                .andExpect(MockMvcResultMatchers.jsonPath("$.size").value(20))
                .andExpect(MockMvcResultMatchers.jsonPath("$.total").value(1))
                .andExpect(MockMvcResultMatchers.jsonPath("$.items[0].id").value(id.toString()))
                .andExpect(MockMvcResultMatchers.jsonPath("$.items[0].processDefinition").value("order"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.items[0].state").value("running"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.items[0].startDate").value("1970-01-01T00:00:02Z"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.items[0].variables").doesNotExist());
    }

    @Test
    void testListWithFilterSortAndPage() throws Exception {
        Mockito.when(this.instances.query("order", false, ProcessInstanceSort.END_DATE, false, 200, 100))
                .thenReturn(new ProcessInstancePage(List.of(), 150));

        this.mvc.perform(MockMvcRequestBuilders.get(BASE).param("definition", "order").param("state", "finished")
                .param("sort", "endDate").param("direction", "desc").param("page", "2").param("size", "500"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.page").value(2))
                .andExpect(MockMvcResultMatchers.jsonPath("$.size").value(100))
                .andExpect(MockMvcResultMatchers.jsonPath("$.total").value(150));
    }

    @Test
    void testListWithInvalidParametersIsBadRequest() throws Exception {
        this.mvc.perform(MockMvcRequestBuilders.get(BASE).param("state", "waiting"))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        this.mvc.perform(MockMvcRequestBuilders.get(BASE).param("sort", "type"))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
        this.mvc.perform(MockMvcRequestBuilders.get(BASE).param("direction", "up"))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
        this.mvc.perform(MockMvcRequestBuilders.get(BASE).param("page", "-1"))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
        this.mvc.perform(MockMvcRequestBuilders.get(BASE).param("size", "0"))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
        this.mvc.perform(MockMvcRequestBuilders.get(BASE).param("page", "x"))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void testGetWithVariablesAndParent() throws Exception {
        final UUID id = UUID.randomUUID();
        final UUID parentInstance = UUID.randomUUID();
        final ProcessStep parentStep = new MockedEngineConfiguration.Step(parentInstance, "call", 1000, null);
        Mockito.when(this.instances.retrieve(id)).thenReturn(new MockedEngineConfiguration.Instance(
                id,
                ORDER,
                Map.of("amount", 3),
                new Date(2000),
                new Date(3000),
                parentStep,
                null));

        this.mvc.perform(MockMvcRequestBuilders.get(BASE + "/" + id))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.state").value("finished"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.endDate").value("1970-01-01T00:00:03Z"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.processDefinitionCreatedDate")
                        .value("1970-01-01T00:00:01Z"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.parentInstanceId").value(parentInstance.toString()))
                .andExpect(MockMvcResultMatchers.jsonPath("$.parentStepId").value(parentStep.id().toString()))
                .andExpect(MockMvcResultMatchers.jsonPath("$.variables.amount").value(3));
    }

    @Test
    void testGetUnknownIsNotFound() throws Exception {
        final UUID id = UUID.randomUUID();
        Mockito.when(this.instances.retrieve(id)).thenThrow(new NoSuchProcessInstanceException(id));

        this.mvc.perform(MockMvcRequestBuilders.get(BASE + "/" + id))
                .andExpect(MockMvcResultMatchers.status().isNotFound());
    }

    @Test
    void testStart() throws Exception {
        final UUID id = UUID.randomUUID();
        Mockito.when(this.engine.startProcess("order", Map.of("amount", 3)))
                .thenReturn(ProcessInstanceControllerTest.instance(id, null));

        this.mvc.perform(MockMvcRequestBuilders.post(BASE).contentType(MediaType.APPLICATION_JSON)
                .content("{\"processDefinition\":\"order\",\"variables\":{\"amount\":3}}"))
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andExpect(MockMvcResultMatchers.header().string("Location",
                        "http://localhost" + BASE + "/" + id))
                .andExpect(MockMvcResultMatchers.jsonPath("$.id").value(id.toString()))
                .andExpect(MockMvcResultMatchers.jsonPath("$.variables.amount").value(3));
    }

    @Test
    void testStartUnknownDefinitionIsNotFound() throws Exception {
        Mockito.when(this.engine.startProcess(ArgumentMatchers.eq("missing"), ArgumentMatchers.any()))
                .thenThrow(new ProcessEngineException("Cannot start process 'missing'.",
                        new NoSuchProcessDefinitionException("missing")));

        this.mvc.perform(MockMvcRequestBuilders.post(BASE).contentType(MediaType.APPLICATION_JSON)
                .content("{\"processDefinition\":\"missing\"}"))
                .andExpect(MockMvcResultMatchers.status().isNotFound());
    }

    @Test
    void testStartFailureIsUnprocessable() throws Exception {
        Mockito.when(this.engine.startProcess(ArgumentMatchers.eq("order"), ArgumentMatchers.any()))
                .thenThrow(new ProcessEngineException("Process definition 'order' has no start event."));

        this.mvc.perform(MockMvcRequestBuilders.post(BASE).contentType(MediaType.APPLICATION_JSON)
                .content("{\"processDefinition\":\"order\"}"))
                .andExpect(MockMvcResultMatchers.status().isUnprocessableContent())
                .andExpect(MockMvcResultMatchers.jsonPath("$.detail")
                        .value("Process definition 'order' has no start event."));
    }

    @Test
    void testStartWithoutDefinitionIsBadRequest() throws Exception {
        this.mvc.perform(MockMvcRequestBuilders.post(BASE).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
        this.mvc.perform(MockMvcRequestBuilders.post(BASE).contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    @Test
    void testStepsWithTypeFilterAndSort() throws Exception {
        final UUID id = UUID.randomUUID();
        Mockito.when(this.instances.retrieve(id)).thenReturn(ProcessInstanceControllerTest.instance(id, null));
        Mockito.when(this.steps.steps(id)).thenReturn(List.of(
                new MockedEngineConfiguration.Step(id, "start", 1000, 1500L),
                new MockedEngineConfiguration.Step(id, "approve", 2000, null)));

        this.mvc.perform(MockMvcRequestBuilders.get(BASE + "/" + id + "/steps"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(2))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].nodeId").value("start"))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].type").value("startEvent"))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].state").value("finished"))
                .andExpect(MockMvcResultMatchers.jsonPath("$[1].type").value("userTask"))
                .andExpect(MockMvcResultMatchers.jsonPath("$[1].nodeName").value("Approve order"))
                .andExpect(MockMvcResultMatchers.jsonPath("$[1].state").value("running"));
        this.mvc.perform(MockMvcRequestBuilders.get(BASE + "/" + id + "/steps").param("state", "running"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(1))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].nodeId").value("approve"));
        this.mvc.perform(MockMvcRequestBuilders.get(BASE + "/" + id + "/steps").param("type", "startEvent"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(1))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].nodeId").value("start"));
        this.mvc.perform(MockMvcRequestBuilders.get(BASE + "/" + id + "/steps").param("sort", "type")
                .param("direction", "desc"))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].type").value("userTask"))
                .andExpect(MockMvcResultMatchers.jsonPath("$[1].type").value("startEvent"));
        this.mvc.perform(MockMvcRequestBuilders.get(BASE + "/" + id + "/steps").param("sort", "endDate")
                .param("direction", "desc"))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].nodeId").value("approve"));
    }

    @Test
    void testStepsOfUnknownInstanceIsNotFound() throws Exception {
        final UUID id = UUID.randomUUID();
        Mockito.when(this.instances.retrieve(id)).thenThrow(new NoSuchProcessInstanceException(id));

        this.mvc.perform(MockMvcRequestBuilders.get(BASE + "/" + id + "/steps"))
                .andExpect(MockMvcResultMatchers.status().isNotFound());
    }
}
