package de.nemeses.spring.bpmn.ui;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * {@code config.json} tells the pages that deployment is enabled, so they show the upload button.
 */
@SpringBootTest(classes = UiTestConfiguration.class, properties = "spring.bpmn.rest.deployment-enabled=true")
class BpmnUiDeploymentEnabledTest {

    @Autowired
    private WebApplicationContext context;

    @Test
    void testConfigHasDeploymentEnabled() throws Exception {
        final MockMvc mvc = MockMvcBuilders.webAppContextSetup(this.context).build();
        mvc.perform(MockMvcRequestBuilders.get("/bpmn-ui/config.json"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.deploymentEnabled").value(true));
    }
}
