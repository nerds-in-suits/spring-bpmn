package de.nemeses.spring.bpmn.rest;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.awaitility.Awaitility;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;

import de.nemeses.spring.bpmn.exec.ProcessEngineException;
import de.nemeses.spring.bpmn.exec.ProcessEngineService;
import de.nemeses.spring.bpmn.repository.ProcessDefinitionService;
import de.nemeses.spring.bpmn.repository.ProcessInstance;
import de.nemeses.spring.bpmn.repository.ProcessInstanceRepository;
import de.nemeses.spring.bpmn.repository.ProcessStep;
import de.nemeses.spring.bpmn.repository.ProcessStepRepository;

/**
 * {@link ProcessInstanceController} on the real engine and repositories.
 */
@SpringBootTest(classes = TestEngineConfiguration.class)
class ProcessInstanceControllerTest {

    private static final String BASE = "/api/bpmn/v1/process-instances";

    private static final String ORDER = "order";

    private static final String STEPS = "/steps";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ProcessEngineService engine;

    @Autowired
    private ProcessDefinitionService definitions;

    @Autowired
    private ProcessInstanceRepository instances;

    @Autowired
    private ProcessStepRepository steps;

    @Autowired
    private TestProcesses processes;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        this.mvc = MockMvcBuilders.webAppContextSetup(this.context).build();
    }

    /**
     * @param date a date of the repository API
     * @return the date as it appears in the JSON responses
     */
    private static String json(final Date date) {
        return Dates.instant(date).toString();
    }

    @Test
    void testListWithDefaults() throws Exception {
        final String name = this.processes.register(ORDER, TestProcesses::order);
        final ProcessInstance older = this.processes.waiting(name, Map.of("amount", 3));
        TestProcesses.after(older.startDate());
        final ProcessInstance newer = this.processes.waiting(name, Map.of("amount", 3));

        final String all = this.mvc.perform(MockMvcRequestBuilders.get(BASE))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.page").value(0))
                .andExpect(MockMvcResultMatchers.jsonPath("$.size").value(20))
                .andExpect(MockMvcResultMatchers.jsonPath("$.total").value(Matchers.greaterThanOrEqualTo(2)))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        // the other tests of this context have instances, too: the first page is full and sorted by start date
        final List<Instant> starts = JsonPath.<List<String>>read(all, "$.items[*].startDate").stream()
                .map(Instant::parse).toList();
        Assertions.assertEquals(Math.min(20, JsonPath.<Integer>read(all, "$.total")), starts.size());
        Assertions.assertEquals(starts.stream().sorted().toList(), starts);
        this.mvc.perform(MockMvcRequestBuilders.get(BASE).param("definition", name))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.total").value(2))
                .andExpect(MockMvcResultMatchers.jsonPath("$.items[0].id").value(older.id().toString()))
                .andExpect(MockMvcResultMatchers.jsonPath("$.items[0].processDefinition").value(name))
                .andExpect(MockMvcResultMatchers.jsonPath("$.items[0].state").value("running"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.items[0].startDate")
                        .value(ProcessInstanceControllerTest.json(older.startDate())))
                .andExpect(MockMvcResultMatchers.jsonPath("$.items[0].variables").doesNotExist())
                .andExpect(MockMvcResultMatchers.jsonPath("$.items[1].id").value(newer.id().toString()));
    }

    @Test
    void testListWithFilterSortAndPage() throws Exception {
        final String name = this.processes.register(ORDER, TestProcesses::order);
        this.processes.waiting(name, Map.of());
        final String[] finished = new String[3];
        for (int i = 0; i < finished.length; i++) {
            final ProcessInstance instance = this.processes.waiting(name, Map.of());
            this.engine.completeUserTask(this.processes.userTasks(instance.id(), 1).getFirst().id(), Map.of());
            finished[i] = this.processes.ended(instance.id()).id().toString();
        }

        this.mvc.perform(MockMvcRequestBuilders.get(BASE).param("definition", name).param("state", "finished")
                .param("sort", "endDate").param("direction", "desc").param("page", "2").param("size", "500"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.page").value(2))
                .andExpect(MockMvcResultMatchers.jsonPath("$.size").value(100))
                .andExpect(MockMvcResultMatchers.jsonPath("$.total").value(3))
                .andExpect(MockMvcResultMatchers.jsonPath("$.items.length()").value(0));
        this.mvc.perform(MockMvcRequestBuilders.get(BASE).param("definition", name).param("state", "finished")
                .param("sort", "endDate").param("direction", "desc").param("page", "0").param("size", "2"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.total").value(3))
                .andExpect(MockMvcResultMatchers.jsonPath("$.items.length()").value(2))
                .andExpect(MockMvcResultMatchers.jsonPath("$.items[0].id").value(finished[2]))
                .andExpect(MockMvcResultMatchers.jsonPath("$.items[1].id").value(finished[1]));
        this.mvc.perform(MockMvcRequestBuilders.get(BASE).param("definition", name).param("state", "finished")
                .param("sort", "endDate").param("direction", "desc").param("page", "1").param("size", "2"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.items.length()").value(1))
                .andExpect(MockMvcResultMatchers.jsonPath("$.items[0].id").value(finished[0]));
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

    /**
     * A child instance started by a call activity, which has ended: its parent, end date and the copied variables.
     */
    @Test
    void testGetWithVariablesAndParent() throws Exception {
        final String child = this.processes.register("child", TestProcesses::straight);
        final String parent = this.processes.register("parent", name -> TestProcesses.document(name,
                "<startEvent id=\"start\"/><callActivity id=\"call\" calledElement=\"" + child + "\"/>"
                + "<userTask id=\"wait\"/><endEvent id=\"end\"/>"
                + "<sequenceFlow id=\"f1\" sourceRef=\"start\" targetRef=\"call\"/>"
                + "<sequenceFlow id=\"f2\" sourceRef=\"call\" targetRef=\"wait\"/>"
                + "<sequenceFlow id=\"f3\" sourceRef=\"wait\" targetRef=\"end\"/>"));
        final ProcessInstance parentInstance = this.processes.waiting(parent, Map.of("amount", 3));
        final ProcessStep call = this.steps.steps(parentInstance.id()).stream()
                .filter(step -> "call".equals(step.nodeId())).findFirst().orElseThrow();
        final ProcessInstance childInstance = this.instances.retrieveChild(call.id()).orElseThrow();
        Assertions.assertNotNull(childInstance.endDate());

        this.mvc.perform(MockMvcRequestBuilders.get(BASE + "/" + childInstance.id()))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.processDefinition").value(child))
                .andExpect(MockMvcResultMatchers.jsonPath("$.state").value("finished"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.endDate")
                        .value(ProcessInstanceControllerTest.json(childInstance.endDate())))
                .andExpect(MockMvcResultMatchers.jsonPath("$.processDefinitionCreatedDate")
                        .value(ProcessInstanceControllerTest.json(this.definitions.retrieveLatest(child)
                                .createdDate())))
                .andExpect(MockMvcResultMatchers.jsonPath("$.parentInstanceId")
                        .value(parentInstance.id().toString()))
                .andExpect(MockMvcResultMatchers.jsonPath("$.parentStepId").value(call.id().toString()))
                .andExpect(MockMvcResultMatchers.jsonPath("$.variables.amount").value(3));
        this.mvc.perform(MockMvcRequestBuilders.get(BASE + "/" + parentInstance.id()))
                .andExpect(MockMvcResultMatchers.jsonPath("$.state").value("running"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.endDate").doesNotExist())
                .andExpect(MockMvcResultMatchers.jsonPath("$.parentInstanceId").doesNotExist());
    }

    @Test
    void testGetUnknownIsNotFound() throws Exception {
        this.mvc.perform(MockMvcRequestBuilders.get(BASE + "/" + UUID.randomUUID()))
                .andExpect(MockMvcResultMatchers.status().isNotFound());
    }

    @Test
    void testBpmnOfInstanceVersion() throws Exception {
        final String name = this.processes.register(ORDER, TestProcesses::order);
        final ProcessInstance instance = this.processes.waiting(name, Map.of());
        this.processes.register(name, TestProcesses.straight(name));

        this.mvc.perform(MockMvcRequestBuilders.get(BASE + "/" + instance.id() + "/bpmn"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                .andExpect(MockMvcResultMatchers.content().string(TestProcesses.order(name)));
    }

    @Test
    void testBpmnOfUnknownInstanceIsNotFound() throws Exception {
        this.mvc.perform(MockMvcRequestBuilders.get(BASE + "/" + UUID.randomUUID() + "/bpmn"))
                .andExpect(MockMvcResultMatchers.status().isNotFound());
    }

    @Test
    void testStart() throws Exception {
        final String name = this.processes.register(ORDER, TestProcesses::order);

        final MvcResult result = this.mvc.perform(MockMvcRequestBuilders.post(BASE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"processDefinition\":\"" + name + "\",\"variables\":{\"amount\":3}}"))
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andExpect(MockMvcResultMatchers.jsonPath("$.processDefinition").value(name))
                .andExpect(MockMvcResultMatchers.jsonPath("$.variables.amount").value(3))
                .andReturn();
        final UUID id = UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8),
                "$.id"));

        Assertions.assertEquals("http://localhost" + BASE + "/" + id, result.getResponse().getHeader("Location"));
        final ProcessInstance instance = this.instances.retrieve(id);
        Assertions.assertEquals(name, instance.processDefinition().name());
        Assertions.assertEquals(Map.of("amount", 3), instance.variables());
    }

    @Test
    void testStartUnknownDefinitionIsNotFound() throws Exception {
        this.mvc.perform(MockMvcRequestBuilders.post(BASE).contentType(MediaType.APPLICATION_JSON)
                .content("{\"processDefinition\":\"" + TestProcesses.uniqueName("missing") + "\"}"))
                .andExpect(MockMvcResultMatchers.status().isNotFound());
    }

    @Test
    void testStartFailureIsUnprocessable() throws Exception {
        final String name = this.processes.register("nostart",
                id -> TestProcesses.document(id, "<userTask id=\"approve\"/>"));

        this.mvc.perform(MockMvcRequestBuilders.post(BASE).contentType(MediaType.APPLICATION_JSON)
                .content("{\"processDefinition\":\"" + name + "\"}"))
                .andExpect(MockMvcResultMatchers.status().isUnprocessableContent())
                .andExpect(MockMvcResultMatchers.jsonPath("$.detail")
                        .value("Process definition '" + name + "' has no start event."));
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
        final String name = this.processes.register(ORDER, TestProcesses::order);
        final UUID id = this.processes.waiting(name, Map.of()).id();
        final String path = BASE + "/" + id + STEPS;

        this.mvc.perform(MockMvcRequestBuilders.get(path))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(2))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].nodeId").value("start"))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].type").value("startEvent"))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].state").value("finished"))
                .andExpect(MockMvcResultMatchers.jsonPath("$[1].type").value("userTask"))
                .andExpect(MockMvcResultMatchers.jsonPath("$[1].nodeName").value("Approve order"))
                .andExpect(MockMvcResultMatchers.jsonPath("$[1].state").value("running"));
        this.mvc.perform(MockMvcRequestBuilders.get(path).param("state", "running"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(1))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].nodeId").value("approve"));
        this.mvc.perform(MockMvcRequestBuilders.get(path).param("type", "startEvent"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(1))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].nodeId").value("start"));
        this.mvc.perform(MockMvcRequestBuilders.get(path).param("sort", "type").param("direction", "desc"))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].type").value("userTask"))
                .andExpect(MockMvcResultMatchers.jsonPath("$[1].type").value("startEvent"));
        this.mvc.perform(MockMvcRequestBuilders.get(path).param("sort", "endDate").param("direction", "desc"))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].nodeId").value("approve"));
    }

    /**
     * A service task without bean fails, and its step carries the error.
     */
    @Test
    void testStepsWithError() throws Exception {
        final String name = this.processes.register("failing", id -> TestProcesses.document(id,
                "<startEvent id=\"start\"/><serviceTask id=\"fail\"/><endEvent id=\"end\"/>"
                + "<sequenceFlow id=\"f1\" sourceRef=\"start\" targetRef=\"fail\"/>"
                + "<sequenceFlow id=\"f2\" sourceRef=\"fail\" targetRef=\"end\"/>"));
        final UUID id = this.engine.startProcess(name, Map.of()).id();
        final ProcessStep failed = Awaitility.await().atMost(Duration.ofSeconds(20))
                .until(() -> this.steps.steps(id).stream().filter(step -> "fail".equals(step.nodeId())).findFirst(),
                        step -> step.isPresent() && step.get().errorDate() != null)
                .orElseThrow();

        this.mvc.perform(MockMvcRequestBuilders.get(BASE + "/" + id + STEPS))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].nodeId").value("start"))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].errorDate").doesNotExist())
                .andExpect(MockMvcResultMatchers.jsonPath("$[1].nodeId").value("fail"))
                .andExpect(MockMvcResultMatchers.jsonPath("$[1].state").value("running"))
                .andExpect(MockMvcResultMatchers.jsonPath("$[1].errorDate")
                        .value(ProcessInstanceControllerTest.json(failed.errorDate())))
                .andExpect(MockMvcResultMatchers.jsonPath("$[1].errorMessage")
                        .value(Matchers.allOf(Matchers.equalTo(failed.errorMessage()),
                                Matchers.startsWith("Service task 'fail' has no attribute"))))
                .andExpect(MockMvcResultMatchers.jsonPath("$[1].errorStackTrace")
                        .value(Matchers.containsString(ProcessEngineException.class.getName() + ": Service task 'fail'")));
    }

    @Test
    void testStepsOfUnknownInstanceIsNotFound() throws Exception {
        this.mvc.perform(MockMvcRequestBuilders.get(BASE + "/" + UUID.randomUUID() + STEPS))
                .andExpect(MockMvcResultMatchers.status().isNotFound());
    }
}
