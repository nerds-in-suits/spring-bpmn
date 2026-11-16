package de.nemeses.spring.bpmn.ui;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * With Spring Boot's default security, the UI needs a login (redirect to the login page), while the REST API next to
 * it answers {@code 401} without redirect.
 */
@SpringBootTest(classes = UiTestConfiguration.class)
class BpmnUiSecurityTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        this.mvc = MockMvcBuilders.webAppContextSetup(this.context).apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    void testUiRedirectsToLogin() throws Exception {
        this.mvc.perform(MockMvcRequestBuilders.get("/bpmn-ui/").accept(MediaType.TEXT_HTML))
                .andExpect(MockMvcResultMatchers.status().is3xxRedirection())
                .andExpect(MockMvcResultMatchers.redirectedUrl("/login"));
    }

    @Test
    void testApiAnswersUnauthorized() throws Exception {
        this.mvc.perform(MockMvcRequestBuilders.get("/api/bpmn/v1/process-definitions").accept(MediaType.TEXT_HTML))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());
    }

    @Test
    void testUiWithLogin() throws Exception {
        this.mvc.perform(MockMvcRequestBuilders.get("/bpmn-ui/config.json")
                .with(SecurityMockMvcRequestPostProcessors.user("user")))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.apiBasePath").value("/api/bpmn/v1"));
    }
}
