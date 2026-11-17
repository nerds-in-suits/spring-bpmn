package de.nemeses.spring.bpmn.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * With {@code spring.bpmn.rest.security.http-basic=false} the REST API ignores HTTP Basic credentials.
 */
@SpringBootTest(classes = { MockedEngineConfiguration.class, BpmnRestSecurityTest.Users.class },
        properties = "spring.bpmn.rest.security.http-basic=false")
class HttpBasicDisabledTest {

    @Autowired
    private WebApplicationContext context;

    @Test
    void testBasicCredentialsAreIgnored() throws Exception {
        MockMvcBuilders.webAppContextSetup(this.context).apply(SecurityMockMvcConfigurers.springSecurity()).build()
                .perform(MockMvcRequestBuilders.get("/api/bpmn/v1/process-definitions")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("admin", "admin")))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());
    }
}
