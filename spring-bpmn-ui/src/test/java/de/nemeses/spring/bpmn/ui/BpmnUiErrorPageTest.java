package de.nemeses.spring.bpmn.ui;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import jakarta.servlet.RequestDispatcher;

/**
 * HTML errors below {@code /bpmn-ui} show the error page of the UI; all other paths and JSON requests keep Spring
 * Boot's error handling. MockMvc does no error dispatch, so the tests call {@code /error} with the attributes the
 * servlet container sets.
 */
@SpringBootTest(classes = UiTestConfiguration.class)
class BpmnUiErrorPageTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        this.mvc = MockMvcBuilders.webAppContextSetup(this.context).build();
    }

    private static MockHttpServletRequestBuilder error(final int status, final String uri) {
        return error("", status, uri);
    }

    private static MockHttpServletRequestBuilder error(final String contextPath, final int status, final String uri) {
        return MockMvcRequestBuilders.get(contextPath + "/error").contextPath(contextPath)
                .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, status)
                .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, uri)
                .requestAttr(RequestDispatcher.ERROR_MESSAGE, "secret detail");
    }

    @Test
    void testNotFoundBelowUi() throws Exception {
        this.mvc.perform(error(404, "/bpmn-ui/nope").accept(MediaType.TEXT_HTML))
                .andExpect(MockMvcResultMatchers.status().isNotFound())
                .andExpect(MockMvcResultMatchers.content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(MockMvcResultMatchers.content().string(Matchers.containsString("<h1>404 Not Found</h1>")))
                .andExpect(MockMvcResultMatchers.content().string(
                        Matchers.containsString("<link rel=\"stylesheet\" href=\"/bpmn-ui/app.css\">")))
                .andExpect(MockMvcResultMatchers.content().string(
                        Matchers.containsString("<a href=\"/bpmn-ui/\">Back to the BPMN UI</a>")))
                .andExpect(MockMvcResultMatchers.content().string(Matchers.not(Matchers.containsString("{{"))))
                .andExpect(MockMvcResultMatchers.content().string(
                        Matchers.not(Matchers.containsString("Whitelabel"))))
                .andExpect(MockMvcResultMatchers.content().string(
                        Matchers.not(Matchers.containsString("secret detail"))));
    }

    @Test
    void testServerErrorBelowUiWithContextPath() throws Exception {
        this.mvc.perform(error("/app", 500, "/app/bpmn-ui").accept(MediaType.TEXT_HTML))
                .andExpect(MockMvcResultMatchers.status().isInternalServerError())
                .andExpect(MockMvcResultMatchers.content().string(
                        Matchers.containsString("<h1>500 Internal Server Error</h1>")))
                .andExpect(MockMvcResultMatchers.content().string(
                        Matchers.containsString("<a href=\"/app/bpmn-ui/\">Back to the BPMN UI</a>")));
    }

    @Test
    void testOtherPathsKeepWhitelabelPage() throws Exception {
        this.mvc.perform(error(404, "/bpmn-uix").accept(MediaType.TEXT_HTML))
                .andExpect(MockMvcResultMatchers.status().isNotFound())
                .andExpect(MockMvcResultMatchers.content().string(Matchers.containsString("Whitelabel")));
    }

    @Test
    void testJsonKeepsErrorAttributes() throws Exception {
        this.mvc.perform(error(404, "/bpmn-ui/nope").accept(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.status().isNotFound())
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value(404));
    }
}
