package de.nemeses.spring.bpmn.ui;

import org.hamcrest.Matchers;
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

/**
 * The UI is served at {@code /bpmn-ui/} with Alpine.js and the bpmn-js viewer, and tells the pages the configured REST
 * base path.
 */
@SpringBootTest(classes = UiTestConfiguration.class, properties = "spring.bpmn.rest.base-path=/bpmn")
class BpmnUiTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        this.mvc = MockMvcBuilders.webAppContextSetup(this.context).build();
    }

    @Test
    void testRedirectsToSlash() throws Exception {
        this.mvc.perform(MockMvcRequestBuilders.get("/bpmn-ui"))
                .andExpect(MockMvcResultMatchers.status().is3xxRedirection())
                .andExpect(MockMvcResultMatchers.redirectedUrl("/bpmn-ui/"));
    }

    @Test
    void testForwardsToEntryPage() throws Exception {
        this.mvc.perform(MockMvcRequestBuilders.get("/bpmn-ui/"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.forwardedUrl("/bpmn-ui/index.html"));
    }

    @Test
    void testServesEntryPage() throws Exception {
        this.mvc.perform(MockMvcRequestBuilders.get("/bpmn-ui/index.html"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(MockMvcResultMatchers.content().string(Matchers.containsString("x-data=\"bpmnUi\"")))
                .andExpect(MockMvcResultMatchers.content().string(
                        Matchers.containsString("../webjars/alpinejs/dist/cdn.min.js")));
        this.mvc.perform(MockMvcRequestBuilders.get("/bpmn-ui/app.js"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().string(Matchers.containsString("Alpine.data('bpmnUi'")));
    }

    @Test
    void testEntryPageCanStartProcess() throws Exception {
        this.mvc.perform(MockMvcRequestBuilders.get("/bpmn-ui/index.html"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().string(Matchers.containsString("@click=\"openStart(")))
                .andExpect(MockMvcResultMatchers.content().string(
                        Matchers.containsString("<dialog id=\"start-dialog\"")));
        this.mvc.perform(MockMvcRequestBuilders.get("/bpmn-ui/app.js"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().string(Matchers.containsString("async confirmStart()")));
    }

    @Test
    void testEntryPageCanUploadDefinition() throws Exception {
        this.mvc.perform(MockMvcRequestBuilders.get("/bpmn-ui/index.html"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().string(
                        Matchers.containsString("@click=\"$refs.uploadFile.click()\"")))
                .andExpect(MockMvcResultMatchers.content().string(Matchers.containsString(
                        "<input type=\"file\" id=\"upload-file\"")))
                .andExpect(MockMvcResultMatchers.content().string(
                        Matchers.containsString("accept=\".bpmn,.xml,application/xml,text/xml\"")));
        this.mvc.perform(MockMvcRequestBuilders.get("/bpmn-ui/app.js"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().string(
                        Matchers.containsString("async uploadDefinition(input)")));
    }

    @Test
    void testEntryPageCanEditVariables() throws Exception {
        this.mvc.perform(MockMvcRequestBuilders.get("/bpmn-ui/index.html"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().string(
                        Matchers.containsString("@submit.prevent=\"saveVariables()\"")))
                .andExpect(MockMvcResultMatchers.content().string(
                        Matchers.containsString("@click=\"removeVariable(index)\"")))
                .andExpect(MockMvcResultMatchers.content().string(
                        Matchers.containsString("@click=\"addVariable()\"")));
        this.mvc.perform(MockMvcRequestBuilders.get("/bpmn-ui/app.js"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().string(Matchers.containsString("async saveVariables()")))
                .andExpect(MockMvcResultMatchers.content().string(
                        Matchers.containsString("'Content-Type': 'application/merge-patch+json'")));
    }

    @Test
    void testEntryPageSortsByColumnHeader() throws Exception {
        this.mvc.perform(MockMvcRequestBuilders.get("/bpmn-ui/index.html"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().string(Matchers.containsString(
                        "<th :aria-sort=\"ariaSort('startDate')\">")))
                .andExpect(MockMvcResultMatchers.content().string(Matchers.containsString(
                        "@click=\"sortBy('type')\"")))
                .andExpect(MockMvcResultMatchers.content().string(
                        Matchers.not(Matchers.containsString("filter('sort'"))));
        this.mvc.perform(MockMvcRequestBuilders.get("/bpmn-ui/app.js"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().string(Matchers.containsString("sortBy(column)")));
    }

    @Test
    void testServesAlpineWebJar() throws Exception {
        this.mvc.perform(MockMvcRequestBuilders.get("/webjars/alpinejs/dist/cdn.min.js"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().string(Matchers.containsString("Alpine")));
    }

    @Test
    void testServesBpmnJsNavigatedViewerWebJar() throws Exception {
        this.mvc.perform(MockMvcRequestBuilders.get("/bpmn-ui/index.html"))
                .andExpect(MockMvcResultMatchers.content().string(
                        Matchers.containsString("../webjars/bpmn-js/dist/bpmn-navigated-viewer.production.min.js")))
                .andExpect(MockMvcResultMatchers.content().string(
                        Matchers.containsString("../webjars/bpmn-js/dist/assets/bpmn-js.css")));
        this.mvc.perform(MockMvcRequestBuilders.get("/webjars/bpmn-js/dist/bpmn-navigated-viewer.production.min.js"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().string(Matchers.containsString("BpmnJS")));
        this.mvc.perform(MockMvcRequestBuilders.get("/webjars/bpmn-js/dist/assets/diagram-js.css"))
                .andExpect(MockMvcResultMatchers.status().isOk());
        this.mvc.perform(MockMvcRequestBuilders.get("/webjars/bpmn-js/dist/assets/bpmn-js.css"))
                .andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    void testEntryPageCanZoomAndFitDiagram() throws Exception {
        this.mvc.perform(MockMvcRequestBuilders.get("/bpmn-ui/index.html"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().string(Matchers.containsString("@click=\"zoomDiagram(1)\"")))
                .andExpect(MockMvcResultMatchers.content().string(
                        Matchers.containsString("@click=\"zoomDiagram(-1)\"")))
                .andExpect(MockMvcResultMatchers.content().string(Matchers.containsString("@click=\"fitDiagram()\"")));
        this.mvc.perform(MockMvcRequestBuilders.get("/bpmn-ui/app.js"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().string(Matchers.containsString("fitDiagram() {")))
                .andExpect(MockMvcResultMatchers.content().string(
                        Matchers.containsString("viewer.get('zoomScroll').stepZoom(direction)")));
        // the navigated viewer has the wheel scroll and zoom (ZoomScroll) that the zoom buttons use
        this.mvc.perform(MockMvcRequestBuilders.get("/webjars/bpmn-js/dist/bpmn-navigated-viewer.production.min.js"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().string(Matchers.containsString("zoomScroll")));
    }

    @Test
    void testEntryPageShowsFailedSteps() throws Exception {
        this.mvc.perform(MockMvcRequestBuilders.get("/bpmn-ui/index.html"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().string(Matchers.containsString(
                        ":class=\"stepState(step)\"")))
                .andExpect(MockMvcResultMatchers.content().string(Matchers.containsString(
                        "<span class=\"state failed\">failed</span>")))
                .andExpect(MockMvcResultMatchers.content().string(Matchers.containsString(
                        "x-text=\"step.errorStackTrace\"")));
        this.mvc.perform(MockMvcRequestBuilders.get("/bpmn-ui/app.js"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().string(Matchers.containsString(
                        "return step.state === 'running' && step.errorDate ? 'failed' : step.state;")))
                .andExpect(MockMvcResultMatchers.content().string(Matchers.containsString(
                        "canvas.addMarker(element, 'step-failed');")));
        this.mvc.perform(MockMvcRequestBuilders.get("/bpmn-ui/app.css"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().string(Matchers.containsString(
                        ".diagram .step-failed:not(.djs-connection)")));
    }

    @Test
    void testEntryPageRefreshesRunningInstance() throws Exception {
        this.mvc.perform(MockMvcRequestBuilders.get("/bpmn-ui/index.html"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().string(Matchers.containsString("x-text=\"refreshHint()\"")));
        this.mvc.perform(MockMvcRequestBuilders.get("/bpmn-ui/app.js"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().string(Matchers.containsString("async refreshInstance() {")))
                .andExpect(MockMvcResultMatchers.content().string(
                        Matchers.containsString("this.refreshInterval = Math.max(0, Number(config.refreshInterval)")))
                .andExpect(MockMvcResultMatchers.content().string(Matchers.containsString("'visibilitychange'")));
        this.mvc.perform(MockMvcRequestBuilders.get("/bpmn-ui/config.json"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.refreshInterval").value(10_000));
    }

    @Test
    void testEntryPageMapsAutoLayoutModules() throws Exception {
        this.mvc.perform(MockMvcRequestBuilders.get("/bpmn-ui/index.html"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().string(
                        Matchers.containsString("<script type=\"importmap\">")))
                .andExpect(MockMvcResultMatchers.content().string(Matchers.containsString(
                        "\"bpmn-auto-layout\": \"./vendor/bpmn-auto-layout/index.js\"")));
        this.mvc.perform(MockMvcRequestBuilders.get("/bpmn-ui/app.js"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().string(
                        Matchers.containsString("await import('bpmn-auto-layout')")));
        this.mvc.perform(MockMvcRequestBuilders.get("/bpmn-ui/vendor/bpmn-auto-layout/index.js"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().string(
                        Matchers.containsString("export { LayoutError, LayoutWarning, layoutProcess };")));
        this.mvc.perform(MockMvcRequestBuilders.get("/bpmn-ui/vendor/bpmn-auto-layout/LICENSE"))
                .andExpect(MockMvcResultMatchers.status().isOk());
        // the bare specifiers imported by bpmn-auto-layout and its dependencies
        for (final String module : new String[] {"bpmn-moddle", "min-dash", "moddle", "moddle-xml", "saxen"}) {
            this.mvc.perform(MockMvcRequestBuilders.get("/bpmn-ui/index.html"))
                    .andExpect(MockMvcResultMatchers.content().string(Matchers.containsString(
                            "\"" + module + "\": \"../webjars/" + module + "/dist/index.js\"")));
            this.mvc.perform(MockMvcRequestBuilders.get("/webjars/" + module + "/dist/index.js"))
                    .andExpect(MockMvcResultMatchers.status().isOk())
                    .andExpect(MockMvcResultMatchers.content().string(Matchers.containsString("export ")));
        }
    }

    @Test
    void testConfigHasBasePath() throws Exception {
        this.mvc.perform(MockMvcRequestBuilders.get("/bpmn-ui/config.json"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.apiBasePath").value("/bpmn"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.deploymentEnabled").value(false));
    }

    @Test
    void testConfigHasContextPath() throws Exception {
        this.mvc.perform(MockMvcRequestBuilders.get("/app/bpmn-ui/config.json").contextPath("/app"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.apiBasePath").value("/app/bpmn"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.logoutPath").value("/app/logout"));
    }

    @Test
    void testConfigHasDefaultLogoutPath() throws Exception {
        this.mvc.perform(MockMvcRequestBuilders.get("/bpmn-ui/config.json"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.logoutPath").value("/logout"));
    }

    @Test
    void testEntryPageHasLogoutLink() throws Exception {
        this.mvc.perform(MockMvcRequestBuilders.get("/bpmn-ui/index.html"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().string(Matchers.containsString(
                        "<a class=\"logout\" :href=\"logoutPath\" x-show=\"logoutPath\">")));
        this.mvc.perform(MockMvcRequestBuilders.get("/bpmn-ui/app.js"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().string(
                        Matchers.containsString("this.logoutPath = config.logoutPath || '';")));
    }
}
