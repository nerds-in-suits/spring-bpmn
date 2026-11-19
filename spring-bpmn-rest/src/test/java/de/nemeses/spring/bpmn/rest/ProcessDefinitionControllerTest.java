package de.nemeses.spring.bpmn.rest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
import de.nemeses.spring.bpmn.repository.ProcessDefinition;
import de.nemeses.spring.bpmn.repository.ProcessDefinitionService;
import de.nemeses.spring.bpmn.repository.ProcessInstance;

/**
 * {@link ProcessDefinitionController} on the real engine and repositories, with deployment enabled.
 */
@SpringBootTest(classes = TestEngineConfiguration.class, properties = "spring.bpmn.rest.deployment-enabled=true")
class ProcessDefinitionControllerTest {

    private static final String BASE = "/api/bpmn/v1/process-definitions";

    private static final String ORDER = "order";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ProcessDefinitionService definitions;

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
     * @param name a definition name
     * @param property a property of the listed definition
     * @return the JSON path of the property of the definition of that name in the list
     */
    private static String listed(final String name, final String property) {
        return "$[?(@.name == '" + name + "')]." + property;
    }

    @Test
    void testListWithInstanceCounts() throws Exception {
        final String order = this.processes.register(ORDER, TestProcesses::order);
        final String other = this.processes.register("other", TestProcesses::straight);
        final List<ProcessInstance> started = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            started.add(this.processes.waiting(order, Map.of()));
        }
        for (final ProcessInstance instance : started.subList(0, 3)) {
            this.engine.completeUserTask(this.processes.userTasks(instance.id(), 1).getFirst().id(), Map.of());
            this.processes.ended(instance.id());
        }
        final ProcessDefinition latest = this.definitions.retrieveLatest(order);

        this.mvc.perform(MockMvcRequestBuilders.get(BASE))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath(ProcessDefinitionControllerTest.listed(order, "createdDate"))
                        .value(Dates.instant(latest.createdDate()).toString()))
                .andExpect(MockMvcResultMatchers.jsonPath(ProcessDefinitionControllerTest.listed(order, "md5Hash"))
                        .value(latest.md5Hash()))
                .andExpect(MockMvcResultMatchers.jsonPath(
                        ProcessDefinitionControllerTest.listed(order, "runningInstances")).value(2))
                .andExpect(MockMvcResultMatchers.jsonPath(
                        ProcessDefinitionControllerTest.listed(order, "finishedInstances")).value(3))
                .andExpect(MockMvcResultMatchers.jsonPath(
                        ProcessDefinitionControllerTest.listed(other, "runningInstances")).value(0))
                .andExpect(MockMvcResultMatchers.jsonPath(
                        ProcessDefinitionControllerTest.listed(other, "finishedInstances")).value(0));
    }

    @Test
    void testPathsAreBelowBasePath() throws Exception {
        this.mvc.perform(MockMvcRequestBuilders.get("/process-definitions"))
                .andExpect(MockMvcResultMatchers.status().isNotFound());
    }

    @Test
    void testLatest() throws Exception {
        final String name = this.processes.register(ORDER, TestProcesses::order);

        this.mvc.perform(MockMvcRequestBuilders.get(BASE + "/" + name))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.name").value(name))
                .andExpect(MockMvcResultMatchers.jsonPath("$.bpmnXml").doesNotExist());
    }

    @Test
    void testLatestUnknownIsNotFound() throws Exception {
        final String name = TestProcesses.uniqueName("missing");

        this.mvc.perform(MockMvcRequestBuilders.get(BASE + "/" + name))
                .andExpect(MockMvcResultMatchers.status().isNotFound())
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value(404))
                .andExpect(MockMvcResultMatchers.jsonPath("$.detail")
                        .value("No process definition found with name '" + name + "'"));
    }

    @Test
    void testVersions() throws Exception {
        final String name = this.processes.register(ORDER, TestProcesses::straight);
        final ProcessDefinition older = this.definitions.retrieveLatest(name);
        TestProcesses.after(older.createdDate());
        this.processes.register(name, TestProcesses.order(name));
        final ProcessDefinition newer = this.definitions.retrieveLatest(name);

        this.mvc.perform(MockMvcRequestBuilders.get(BASE + "/" + name + "/versions"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(2))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].createdDate")
                        .value(Dates.instant(newer.createdDate()).toString()))
                .andExpect(MockMvcResultMatchers.jsonPath("$[1].createdDate")
                        .value(Dates.instant(older.createdDate()).toString()));
    }

    @Test
    void testVersionsOfUnknownIsNotFound() throws Exception {
        this.mvc.perform(MockMvcRequestBuilders.get(BASE + "/" + TestProcesses.uniqueName("missing")
                + "/versions"))
                .andExpect(MockMvcResultMatchers.status().isNotFound());
    }

    @Test
    void testBpmn() throws Exception {
        final String name = this.processes.register(ORDER, TestProcesses::order);

        this.mvc.perform(MockMvcRequestBuilders.get(BASE + "/" + name + "/bpmn"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                .andExpect(MockMvcResultMatchers.content().string(TestProcesses.order(name)));
    }

    @Test
    void testDeployRegistersNewProcess() throws Exception {
        final String name = TestProcesses.uniqueName(ORDER);
        final String xml = TestProcesses.order(name);

        this.mvc.perform(MockMvcRequestBuilders.post(BASE).contentType(MediaType.APPLICATION_XML).content(xml))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].name").value(name))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].registered").value(true));

        final List<ProcessDefinition> versions = List.copyOf(this.definitions.retrieveAll(name));
        Assertions.assertEquals(1, versions.size());
        Assertions.assertEquals(xml, versions.getFirst().bpmnXml());
        Assertions.assertNotNull(versions.getFirst().createdDate());
    }

    @Test
    void testDeploySkipsUnchangedProcess() throws Exception {
        final String name = this.processes.register(ORDER, TestProcesses::order);

        this.mvc.perform(MockMvcRequestBuilders.post(BASE).contentType(MediaType.APPLICATION_XML)
                .content(TestProcesses.order(name)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].registered").value(false));

        Assertions.assertEquals(1, this.definitions.retrieveAll(name).size());
    }

    @Test
    void testDeployInvalidDocumentIsBadRequest() throws Exception {
        final int before = this.definitions.retrieveLatestAll().size();

        this.mvc.perform(MockMvcRequestBuilders.post(BASE).contentType(MediaType.APPLICATION_XML)
                .content("<definitions/>"))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

        Assertions.assertEquals(before, this.definitions.retrieveLatestAll().size());
    }
}
