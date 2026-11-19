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

import de.nemeses.spring.bpmn.repository.ProcessInstanceRepository;

/**
 * {@link ProcessVariableController} on the real engine and repositories: the variables of instances that wait in a
 * user task, so no step changes them meanwhile.
 */
@SpringBootTest(classes = TestEngineConfiguration.class)
class ProcessVariableControllerTest {

    private static final String BASE = "/api/bpmn/v1/process-instances/";

    private static final String VARIABLES = "/variables";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ProcessInstanceRepository instances;

    @Autowired
    private TestProcesses processes;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        this.mvc = MockMvcBuilders.webAppContextSetup(this.context).build();
    }

    /**
     * @param variables the initial variables
     * @return the id of a new instance waiting in a user task
     */
    private UUID waiting(final Map<String, Object> variables) throws Exception {
        return this.processes.waiting(this.processes.register("order", TestProcesses::order), variables)
                .id();
    }

    /**
     * @param id an instance id
     * @return its stored variables
     */
    private Map<String, Object> variables(final UUID id) throws Exception {
        return this.instances.retrieve(id).variables();
    }

    @Test
    void testList() throws Exception {
        final UUID id = this.waiting(Map.of("amount", 3, "name", "x"));

        this.mvc.perform(MockMvcRequestBuilders.get(BASE + id + VARIABLES))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.amount").value(3))
                .andExpect(MockMvcResultMatchers.jsonPath("$.name").value("x"));
    }

    @Test
    void testListOfUnknownInstanceIsNotFound() throws Exception {
        this.mvc.perform(MockMvcRequestBuilders.get(BASE + UUID.randomUUID() + VARIABLES))
                .andExpect(MockMvcResultMatchers.status().isNotFound())
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void testMerge() throws Exception {
        final UUID id = this.waiting(Map.of("amount", 3, "note", "n", "keep", "k"));

        this.mvc.perform(MockMvcRequestBuilders.patch(BASE + id + VARIABLES)
                .contentType("application/merge-patch+json")
                .content("{\"amount\":4,\"note\":null,\"address\":{\"city\":\"Berlin\"}}"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.amount").value(4))
                .andExpect(MockMvcResultMatchers.jsonPath("$.note").doesNotExist())
                .andExpect(MockMvcResultMatchers.jsonPath("$.keep").value("k"));

        Assertions.assertEquals(Map.of("amount", 4, "keep", "k", "address", Map.of("city", "Berlin")),
                this.variables(id));
    }

    @Test
    void testReplace() throws Exception {
        final UUID id = this.waiting(Map.of("amount", 3));

        this.mvc.perform(MockMvcRequestBuilders.put(BASE + id + VARIABLES).contentType(MediaType.APPLICATION_JSON)
                .content("{\"a\":[1,2]}"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.a[1]").value(2))
                .andExpect(MockMvcResultMatchers.jsonPath("$.amount").doesNotExist());

        Assertions.assertEquals(Map.of("a", List.of(1, 2)), this.variables(id));
    }

    @Test
    void testInvalidBodiesAreBadRequest() throws Exception {
        final UUID id = this.waiting(Map.of("a", 1));

        this.mvc.perform(MockMvcRequestBuilders.patch(BASE + id + VARIABLES).contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        this.mvc.perform(MockMvcRequestBuilders.put(BASE + id + VARIABLES).contentType(MediaType.APPLICATION_JSON)
                .content("[1]"))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
        this.mvc.perform(MockMvcRequestBuilders.patch(BASE + id + VARIABLES).contentType(MediaType.APPLICATION_JSON)
                .content("{nope"))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
        this.mvc.perform(MockMvcRequestBuilders.put(BASE + id + VARIABLES + "/a")
                .contentType(MediaType.APPLICATION_JSON).content("null"))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());

        Assertions.assertEquals(Map.of("a", 1), this.variables(id));
    }

    @Test
    void testUpdateFailures() throws Exception {
        final UUID unknown = UUID.randomUUID();
        final UUID ended = this.processes.finished(this.processes.register("straight",
                TestProcesses::straight), Map.of("a", 0)).id();

        this.mvc.perform(MockMvcRequestBuilders.patch(BASE + unknown + VARIABLES)
                .contentType(MediaType.APPLICATION_JSON).content("{\"a\":1}"))
                .andExpect(MockMvcResultMatchers.status().isNotFound());
        this.mvc.perform(MockMvcRequestBuilders.put(BASE + ended + VARIABLES).contentType(MediaType.APPLICATION_JSON)
                .content("{\"a\":1}"))
                .andExpect(MockMvcResultMatchers.status().isUnprocessableContent())
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        this.mvc.perform(MockMvcRequestBuilders.put(BASE + ended + VARIABLES + "/a")
                .contentType(MediaType.APPLICATION_JSON).content("1"))
                .andExpect(MockMvcResultMatchers.status().isUnprocessableContent());
        this.mvc.perform(MockMvcRequestBuilders.delete(BASE + unknown + VARIABLES + "/a"))
                .andExpect(MockMvcResultMatchers.status().isNotFound());

        Assertions.assertEquals(Map.of("a", 0), this.variables(ended));
    }

    @Test
    void testGetSingle() throws Exception {
        final UUID id = this.waiting(Map.of("name", "x", "address", Map.of("city", "Berlin")));

        this.mvc.perform(MockMvcRequestBuilders.get(BASE + id + VARIABLES + "/name"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.content().json("\"x\""));
        this.mvc.perform(MockMvcRequestBuilders.get(BASE + id + VARIABLES + "/address"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.city").value("Berlin"));
        this.mvc.perform(MockMvcRequestBuilders.get(BASE + id + VARIABLES + "/missing"))
                .andExpect(MockMvcResultMatchers.status().isNotFound())
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void testSetSingle() throws Exception {
        final UUID id = this.waiting(Map.of("name", "x", "amount", 3));

        this.mvc.perform(MockMvcRequestBuilders.put(BASE + id + VARIABLES + "/name")
                .contentType(MediaType.APPLICATION_JSON).content("\"y\""))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().json("\"y\""));

        Assertions.assertEquals(Map.of("name", "y", "amount", 3), this.variables(id));
    }

    @Test
    void testDeleteSingle() throws Exception {
        final UUID id = this.waiting(Map.of("name", "x", "amount", 3));

        this.mvc.perform(MockMvcRequestBuilders.delete(BASE + id + VARIABLES + "/name"))
                .andExpect(MockMvcResultMatchers.status().isNoContent());

        Assertions.assertEquals(Map.of("amount", 3), this.variables(id));
    }
}
