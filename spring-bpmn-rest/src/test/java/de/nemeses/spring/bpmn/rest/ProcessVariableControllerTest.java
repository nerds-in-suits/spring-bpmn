package de.nemeses.spring.bpmn.rest;

import java.util.Date;
import java.util.HashMap;
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
import de.nemeses.spring.bpmn.repository.ProcessInstance;
import de.nemeses.spring.bpmn.repository.ProcessInstanceRepository;

/**
 * {@link ProcessVariableController} on a mocked engine.
 */
@SpringBootTest(classes = MockedEngineConfiguration.class)
class ProcessVariableControllerTest {

    private static final String BASE = "/api/bpmn/v1/process-instances/";

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

    private static ProcessInstance instance(final UUID id, final Map<String, Object> variables) {
        return new MockedEngineConfiguration.Instance(
                id,
                new MockedEngineConfiguration.Definition("order", new Date(1000), MockedEngineConfiguration.ORDER_XML),
                variables,
                new Date(2000),
                null,
                null,
                null);
    }

    @Test
    void testList() throws Exception {
        final UUID id = UUID.randomUUID();
        Mockito.when(this.instances.retrieve(id))
                .thenReturn(ProcessVariableControllerTest.instance(id, Map.of("amount", 3, "name", "x")));

        this.mvc.perform(MockMvcRequestBuilders.get(BASE + id + "/variables"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.amount").value(3))
                .andExpect(MockMvcResultMatchers.jsonPath("$.name").value("x"));
    }

    @Test
    void testListOfUnknownInstanceIsNotFound() throws Exception {
        final UUID id = UUID.randomUUID();
        Mockito.when(this.instances.retrieve(id)).thenThrow(new NoSuchProcessInstanceException(id));

        this.mvc.perform(MockMvcRequestBuilders.get(BASE + id + "/variables"))
                .andExpect(MockMvcResultMatchers.status().isNotFound())
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void testMerge() throws Exception {
        final UUID id = UUID.randomUUID();
        final Map<String, Object> changes = new HashMap<>();
        changes.put("amount", 4);
        changes.put("note", null);
        changes.put("address", Map.of("city", "Berlin"));
        Mockito.when(this.engine.updateVariables(id, changes, false))
                .thenReturn(ProcessVariableControllerTest.instance(id, Map.of("amount", 4)));

        this.mvc.perform(MockMvcRequestBuilders.patch(BASE + id + "/variables")
                .contentType("application/merge-patch+json")
                .content("{\"amount\":4,\"note\":null,\"address\":{\"city\":\"Berlin\"}}"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.amount").value(4));
        Mockito.verify(this.engine).updateVariables(id, changes, false);
    }

    @Test
    void testReplace() throws Exception {
        final UUID id = UUID.randomUUID();
        Mockito.when(this.engine.updateVariables(id, Map.of("a", List.of(1, 2)), true))
                .thenReturn(ProcessVariableControllerTest.instance(id, Map.of("a", List.of(1, 2))));

        this.mvc.perform(MockMvcRequestBuilders.put(BASE + id + "/variables").contentType(MediaType.APPLICATION_JSON)
                .content("{\"a\":[1,2]}"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.a[1]").value(2));
    }

    @Test
    void testInvalidBodiesAreBadRequest() throws Exception {
        final UUID id = UUID.randomUUID();
        this.mvc.perform(MockMvcRequestBuilders.patch(BASE + id + "/variables").contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        this.mvc.perform(MockMvcRequestBuilders.put(BASE + id + "/variables").contentType(MediaType.APPLICATION_JSON)
                .content("[1]"))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
        this.mvc.perform(MockMvcRequestBuilders.patch(BASE + id + "/variables").contentType(MediaType.APPLICATION_JSON)
                .content("{nope"))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
        this.mvc.perform(MockMvcRequestBuilders.put(BASE + id + "/variables/a").contentType(MediaType.APPLICATION_JSON)
                .content("null"))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
        Mockito.verifyNoInteractions(this.engine);
    }

    @Test
    void testUpdateFailures() throws Exception {
        final UUID unknown = UUID.randomUUID();
        final UUID ended = UUID.randomUUID();
        Mockito.when(this.engine.updateVariables(ArgumentMatchers.eq(unknown), ArgumentMatchers.any(),
                ArgumentMatchers.anyBoolean()))
                .thenThrow(new ProcessEngineException("unknown", new NoSuchProcessInstanceException(unknown)));
        Mockito.when(this.engine.updateVariables(ArgumentMatchers.eq(ended), ArgumentMatchers.any(),
                ArgumentMatchers.anyBoolean()))
                .thenThrow(new ProcessEngineException("ended"));

        this.mvc.perform(MockMvcRequestBuilders.patch(BASE + unknown + "/variables")
                .contentType(MediaType.APPLICATION_JSON).content("{\"a\":1}"))
                .andExpect(MockMvcResultMatchers.status().isNotFound());
        this.mvc.perform(MockMvcRequestBuilders.put(BASE + ended + "/variables").contentType(MediaType.APPLICATION_JSON)
                .content("{\"a\":1}"))
                .andExpect(MockMvcResultMatchers.status().isUnprocessableContent())
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        this.mvc.perform(MockMvcRequestBuilders.put(BASE + ended + "/variables/a")
                .contentType(MediaType.APPLICATION_JSON).content("1"))
                .andExpect(MockMvcResultMatchers.status().isUnprocessableContent());
        this.mvc.perform(MockMvcRequestBuilders.delete(BASE + unknown + "/variables/a"))
                .andExpect(MockMvcResultMatchers.status().isNotFound());
    }

    @Test
    void testGetSingle() throws Exception {
        final UUID id = UUID.randomUUID();
        Mockito.when(this.instances.retrieve(id))
                .thenReturn(ProcessVariableControllerTest.instance(id, Map.of("name", "x", "address",
                        Map.of("city", "Berlin"))));

        this.mvc.perform(MockMvcRequestBuilders.get(BASE + id + "/variables/name"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.content().json("\"x\""));
        this.mvc.perform(MockMvcRequestBuilders.get(BASE + id + "/variables/address"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.city").value("Berlin"));
        this.mvc.perform(MockMvcRequestBuilders.get(BASE + id + "/variables/missing"))
                .andExpect(MockMvcResultMatchers.status().isNotFound())
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void testSetSingle() throws Exception {
        final UUID id = UUID.randomUUID();
        Mockito.when(this.engine.updateVariables(id, Map.of("name", "y"), false))
                .thenReturn(ProcessVariableControllerTest.instance(id, Map.of("name", "y", "amount", 3)));

        this.mvc.perform(MockMvcRequestBuilders.put(BASE + id + "/variables/name")
                .contentType(MediaType.APPLICATION_JSON).content("\"y\""))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().json("\"y\""));
        Mockito.verify(this.engine).updateVariables(id, Map.of("name", "y"), false);
    }

    @Test
    void testDeleteSingle() throws Exception {
        final UUID id = UUID.randomUUID();
        final Map<String, Object> removal = new HashMap<>();
        removal.put("name", null);
        Mockito.when(this.engine.updateVariables(id, removal, false))
                .thenReturn(ProcessVariableControllerTest.instance(id, Map.of()));

        this.mvc.perform(MockMvcRequestBuilders.delete(BASE + id + "/variables/name"))
                .andExpect(MockMvcResultMatchers.status().isNoContent());
        Mockito.verify(this.engine).updateVariables(id, removal, false);
    }
}
