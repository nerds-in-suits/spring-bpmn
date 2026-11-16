package de.nemeses.spring.bpmn.rest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import jakarta.servlet.http.Cookie;

/**
 * The security filter chain of the REST API: {@code 401} and {@code 403} problem details, the authority of each
 * operation, CSRF protection of cookie sessions and HTTP Basic.
 */
@SpringBootTest(classes = { MockedEngineConfiguration.class, BpmnRestSecurityTest.Users.class }, properties = {
    "spring.bpmn.rest.deployment-enabled=true",
    "spring.bpmn.rest.security.authority.read-definitions=BPMN_READ",
    "spring.bpmn.rest.security.authority.read-instances=BPMN_READ",
    "spring.bpmn.rest.security.authority.deploy=BPMN_DEPLOY",
    "spring.bpmn.rest.security.authority.start-instance=BPMN_START",
    "spring.bpmn.rest.security.authority.update-variables=BPMN_VARIABLES" })
class BpmnRestSecurityTest {

    private static final String BASE = "/api/bpmn/v1";

    private static final String[] ALL = {"BPMN_READ", "BPMN_DEPLOY", "BPMN_START", "BPMN_VARIABLES"};

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private RequestMappingHandlerMapping mappings;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        this.mvc = MockMvcBuilders.webAppContextSetup(this.context).apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    private static String complete() {
        return BASE + "/user-tasks/" + UUID.randomUUID() + "/complete";
    }

    @Test
    void testUnauthenticatedIsUnauthorized() throws Exception {
        this.mvc.perform(MockMvcRequestBuilders.get(BASE + "/process-definitions"))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized())
                .andExpect(MockMvcResultMatchers.content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value(401))
                .andExpect(MockMvcResultMatchers.header().doesNotExist(HttpHeaders.LOCATION))
                .andExpect(MockMvcResultMatchers.header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE))
                .andExpect(MockMvcResultMatchers.request().sessionAttributeDoesNotExist(
                        "SPRING_SECURITY_SAVED_REQUEST"));
    }

    @Test
    void testMissingAuthorityIsForbidden() throws Exception {
        this.mvc.perform(MockMvcRequestBuilders.get(BASE + "/process-definitions")
                .with(SecurityMockMvcRequestPostProcessors.user("nobody").authorities(List.of())))
                .andExpect(MockMvcResultMatchers.status().isForbidden())
                .andExpect(MockMvcResultMatchers.content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value(403));
    }

    @Test
    void testReadAuthority() throws Exception {
        this.mvc.perform(MockMvcRequestBuilders.get(BASE + "/process-definitions")
                .with(SecurityMockMvcRequestPostProcessors.user("viewer").authorities(
                        new SimpleGrantedAuthority("BPMN_READ"))))
                .andExpect(MockMvcResultMatchers.status().isOk());
        this.mvc.perform(MockMvcRequestBuilders.post(BASE + "/process-instances").contentType(MediaType.APPLICATION_JSON)
                .content("{\"processDefinition\":\"order\"}")
                .with(SecurityMockMvcRequestPostProcessors.httpBasic("viewer", "viewer")))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
    }

    @Test
    void testEmptyAuthorityNeedsAuthenticationOnly() throws Exception {
        this.mvc.perform(MockMvcRequestBuilders.post(BpmnRestSecurityTest.complete())
                .with(SecurityMockMvcRequestPostProcessors.httpBasic("nobody", "nobody")))
                .andExpect(MockMvcResultMatchers.status().isNoContent());
        // exempt from CSRF by the Authorization header, but still not authenticated
        this.mvc.perform(MockMvcRequestBuilders.post(BpmnRestSecurityTest.complete())
                .header(HttpHeaders.AUTHORIZATION, "Bearer unknown"))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());
    }

    @Test
    void testUnknownPathIsDenied() throws Exception {
        this.mvc.perform(MockMvcRequestBuilders.get(BASE + "/unknown")
                .with(SecurityMockMvcRequestPostProcessors.httpBasic("admin", "admin")))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
    }

    @Test
    void testCsrfForSessionAuthentication() throws Exception {
        final MvcResult read = this.mvc.perform(MockMvcRequestBuilders.get(BASE + "/process-definitions")
                .with(SecurityMockMvcRequestPostProcessors.user("nobody").authorities(
                        new SimpleGrantedAuthority("BPMN_READ"))))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.cookie().exists("XSRF-TOKEN"))
                .andReturn();
        final Cookie token = read.getResponse().getCookie("XSRF-TOKEN");

        this.mvc.perform(MockMvcRequestBuilders.post(BpmnRestSecurityTest.complete())
                .with(SecurityMockMvcRequestPostProcessors.user("nobody")))
                .andExpect(MockMvcResultMatchers.status().isForbidden())
                .andExpect(MockMvcResultMatchers.content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        this.mvc.perform(MockMvcRequestBuilders.post(BpmnRestSecurityTest.complete())
                .with(SecurityMockMvcRequestPostProcessors.user("nobody"))
                .cookie(token).header("X-XSRF-TOKEN", "wrong"))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
        this.mvc.perform(MockMvcRequestBuilders.post(BpmnRestSecurityTest.complete())
                .with(SecurityMockMvcRequestPostProcessors.user("nobody"))
                .cookie(token).header("X-XSRF-TOKEN", token.getValue()))
                .andExpect(MockMvcResultMatchers.status().isNoContent());
    }

    @Test
    void testHttpBasicNeedsNoCsrf() throws Exception {
        this.mvc.perform(MockMvcRequestBuilders.post(BpmnRestSecurityTest.complete())
                .with(SecurityMockMvcRequestPostProcessors.httpBasic("admin", "admin")))
                .andExpect(MockMvcResultMatchers.status().isNoContent());
        this.mvc.perform(MockMvcRequestBuilders.post(BpmnRestSecurityTest.complete())
                .with(SecurityMockMvcRequestPostProcessors.httpBasic("admin", "wrong")))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized())
                .andExpect(MockMvcResultMatchers.header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE));
    }

    /**
     * Every endpoint of the controllers belongs to an operation: a user with all authorities passes the security
     * chain, a user without authorities is rejected unless the operation has no authority
     * ({@code complete-user-task} here).
     */
    @Test
    void testEveryEndpointHasAnOperation() throws Exception {
        int endpoints = 0;
        for (final Map.Entry<RequestMappingInfo, HandlerMethod> entry : this.mappings
                .getHandlerMethods().entrySet()) {
            if (!entry.getValue().getBeanType().getPackageName().equals(BpmnRestSecurityTest.class.getPackageName())) {
                continue;
            }
            final boolean open = entry.getValue().getMethod().getName().equals("complete");
            for (final String pattern : entry.getKey().getPatternValues()) {
                final String path = pattern.replaceAll("\\{[^}]*}", UUID.randomUUID().toString());
                for (final HttpMethod method : entry.getKey().getMethodsCondition().getMethods().stream()
                        .map(requestMethod -> requestMethod.asHttpMethod()).toList()) {
                    endpoints++;
                    Assertions.assertTrue(path.startsWith(BASE + "/"), path);
                    final int admin = this.status(MockMvcRequestBuilders.request(method,
                            path).with(SecurityMockMvcRequestPostProcessors.httpBasic("admin", "admin")));
                    Assertions.assertTrue(admin != 401 && admin != 403, method + " " + path + ": " + admin);
                    final int nobody = this.status(MockMvcRequestBuilders.request(method,
                            path).with(SecurityMockMvcRequestPostProcessors.httpBasic("nobody", "nobody")));
                    if (open) {
                        Assertions.assertNotEquals(403, nobody, method + " " + path);
                    } else {
                        Assertions.assertEquals(403, nobody, method + " " + path);
                    }
                }
            }
        }
        Assertions.assertTrue(endpoints >= 14, "endpoints: " + endpoints);
    }

    /**
     * @return the status of the response, or {@code 500} if the controller threw past the security chain
     */
    @SuppressWarnings({"checkstyle:IllegalCatch", "PMD.AvoidCatchingGenericException"})
    private int status(final MockHttpServletRequestBuilder request) {
        try {
            return this.mvc.perform(request).andReturn().getResponse().getStatus();
        } catch (final Exception e) {
            return 500;
        }
    }

    /**
     * Users with a password equal to their name: {@code admin} with all authorities, {@code viewer} with
     * {@code BPMN_READ} and {@code nobody} without authorities.
     */
    @Configuration(proxyBeanMethods = false)
    static class Users {

        @Bean
        UserDetailsService userDetailsService() {
            return new InMemoryUserDetailsManager(
                    User.withUsername("admin").password("{noop}admin").authorities(ALL).build(),
                    User.withUsername("viewer").password("{noop}viewer").authorities("BPMN_READ").build(),
                    User.withUsername("nobody").password("{noop}nobody").authorities(new String[0]).build());
        }
    }
}
