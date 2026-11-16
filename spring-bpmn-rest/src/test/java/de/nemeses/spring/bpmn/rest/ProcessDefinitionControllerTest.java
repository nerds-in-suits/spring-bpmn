package de.nemeses.spring.bpmn.rest;

import java.util.Date;
import java.util.List;
import java.util.Map;

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

import de.nemeses.spring.bpmn.repository.NoSuchProcessDefinitionException;
import de.nemeses.spring.bpmn.repository.ProcessDefinition;
import de.nemeses.spring.bpmn.repository.ProcessDefinitionService;
import de.nemeses.spring.bpmn.repository.ProcessInstanceCount;
import de.nemeses.spring.bpmn.repository.ProcessInstanceRepository;

/**
 * {@link ProcessDefinitionController} on mocked repositories, with deployment enabled.
 */
@SpringBootTest(classes = MockedEngineConfiguration.class, properties = "spring.bpmn.rest.deployment-enabled=true")
class ProcessDefinitionControllerTest {

    private static final String BASE = "/api/bpmn/v1/process-definitions";

    private static final ProcessDefinition ORDER = new MockedEngineConfiguration.Definition(
            "order",
            new Date(1000),
            MockedEngineConfiguration.ORDER_XML);

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ProcessDefinitionService definitions;

    @Autowired
    private ProcessInstanceRepository instances;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        this.mvc = MockMvcBuilders.webAppContextSetup(this.context).build();
    }

    @AfterEach
    void tearDown() {
        Mockito.reset(this.definitions, this.instances);
    }

    @Test
    void testListWithInstanceCounts() throws Exception {
        final ProcessDefinition other = new MockedEngineConfiguration.Definition("other", new Date(2000), "<x/>");
        Mockito.when(this.definitions.retrieveLatestAll()).thenReturn(List.of(ORDER, other));
        Mockito.when(this.instances.countByDefinition()).thenReturn(Map.of("order", new ProcessInstanceCount(2, 3)));

        this.mvc.perform(MockMvcRequestBuilders.get(BASE))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].name").value("order"))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].createdDate").value("1970-01-01T00:00:01Z"))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].md5Hash").value(ORDER.md5Hash()))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].runningInstances").value(2))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].finishedInstances").value(3))
                .andExpect(MockMvcResultMatchers.jsonPath("$[1].name").value("other"))
                .andExpect(MockMvcResultMatchers.jsonPath("$[1].runningInstances").value(0))
                .andExpect(MockMvcResultMatchers.jsonPath("$[1].finishedInstances").value(0));
    }

    @Test
    void testPathsAreBelowBasePath() throws Exception {
        this.mvc.perform(MockMvcRequestBuilders.get("/process-definitions"))
                .andExpect(MockMvcResultMatchers.status().isNotFound());
    }

    @Test
    void testLatest() throws Exception {
        Mockito.when(this.definitions.retrieveLatest("order")).thenReturn(ORDER);

        this.mvc.perform(MockMvcRequestBuilders.get(BASE + "/order"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.name").value("order"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.bpmnXml").doesNotExist());
    }

    @Test
    void testLatestUnknownIsNotFound() throws Exception {
        Mockito.when(this.definitions.retrieveLatest("missing"))
                .thenThrow(new NoSuchProcessDefinitionException("missing"));

        this.mvc.perform(MockMvcRequestBuilders.get(BASE + "/missing"))
                .andExpect(MockMvcResultMatchers.status().isNotFound())
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value(404))
                .andExpect(MockMvcResultMatchers.jsonPath("$.detail")
                        .value("No process definition found with name 'missing'"));
    }

    @Test
    void testVersions() throws Exception {
        final ProcessDefinition older = new MockedEngineConfiguration.Definition("order", new Date(500), "<old/>");
        Mockito.when(this.definitions.retrieveAll("order")).thenReturn(List.of(ORDER, older));

        this.mvc.perform(MockMvcRequestBuilders.get(BASE + "/order/versions"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(2))
                .andExpect(MockMvcResultMatchers.jsonPath("$[1].createdDate").value("1970-01-01T00:00:00.500Z"));
    }

    @Test
    void testVersionsOfUnknownIsNotFound() throws Exception {
        Mockito.when(this.definitions.retrieveAll("missing")).thenReturn(List.of());

        this.mvc.perform(MockMvcRequestBuilders.get(BASE + "/missing/versions"))
                .andExpect(MockMvcResultMatchers.status().isNotFound());
    }

    @Test
    void testBpmn() throws Exception {
        Mockito.when(this.definitions.retrieveLatest("order")).thenReturn(ORDER);

        this.mvc.perform(MockMvcRequestBuilders.get(BASE + "/order/bpmn"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                .andExpect(MockMvcResultMatchers.content().string(MockedEngineConfiguration.ORDER_XML));
    }

    @Test
    void testDeployRegistersNewProcess() throws Exception {
        Mockito.when(this.definitions.retrieveLatest("order"))
                .thenThrow(new NoSuchProcessDefinitionException("order"))
                .thenReturn(ORDER);

        this.mvc.perform(MockMvcRequestBuilders.post(BASE).contentType(MediaType.APPLICATION_XML)
                .content(MockedEngineConfiguration.ORDER_XML))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].name").value("order"))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].registered").value(true));

        Mockito.verify(this.definitions).register(ArgumentMatchers.argThat(definition -> "order".equals(
                definition.name()) && MockedEngineConfiguration.ORDER_XML.equals(definition.bpmnXml())
                && definition.createdDate() == null));
    }

    @Test
    void testDeploySkipsUnchangedProcess() throws Exception {
        Mockito.when(this.definitions.retrieveLatest("order")).thenReturn(ORDER);

        this.mvc.perform(MockMvcRequestBuilders.post(BASE).contentType(MediaType.APPLICATION_XML)
                .content(MockedEngineConfiguration.ORDER_XML))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].registered").value(false));

        Mockito.verify(this.definitions, Mockito.never()).register(ArgumentMatchers.any());
    }

    @Test
    void testDeployInvalidDocumentIsBadRequest() throws Exception {
        this.mvc.perform(MockMvcRequestBuilders.post(BASE).contentType(MediaType.APPLICATION_XML)
                .content("<definitions/>"))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

        Mockito.verify(this.definitions, Mockito.never()).register(ArgumentMatchers.any());
    }
}
