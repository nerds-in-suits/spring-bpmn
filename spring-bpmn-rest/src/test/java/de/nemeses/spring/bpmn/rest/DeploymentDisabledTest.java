package de.nemeses.spring.bpmn.rest;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import de.nemeses.spring.bpmn.repository.ProcessDefinitionService;

/**
 * Deployment is rejected by default, and the base path is configurable.
 */
@SpringBootTest(classes = MockedEngineConfiguration.class, properties = "spring.bpmn.rest.base-path=/bpmn")
class DeploymentDisabledTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ProcessDefinitionService definitions;

    @Test
    void testDeployIsForbidden() throws Exception {
        MockMvcBuilders.webAppContextSetup(this.context).build()
                .perform(MockMvcRequestBuilders.post("/bpmn/process-definitions")
                        .contentType(MediaType.APPLICATION_XML).content(MockedEngineConfiguration.ORDER_XML))
                .andExpect(MockMvcResultMatchers.status().isForbidden())
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

        Mockito.verify(this.definitions, Mockito.never()).register(ArgumentMatchers.any());
    }
}
