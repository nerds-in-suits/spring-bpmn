package de.nemeses.spring.bpmn.ui;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import de.nemeses.spring.bpmn.rest.BpmnRestProperties;
import jakarta.servlet.http.HttpServletRequest;

/**
 * {@code GET /bpmn-ui/config.json}: the configuration the pages of the UI read on startup, so they follow
 * {@code spring.bpmn.rest.base-path} and the servlet context path:
 * <pre>
 * { "apiBasePath": "&lt;context path&gt;&lt;spring.bpmn.rest.base-path&gt;",
 *   "deploymentEnabled": &lt;spring.bpmn.rest.deployment-enabled&gt;,
 *   "logoutPath": "&lt;context path&gt;&lt;spring.bpmn.ui.logout-path&gt;",
 *   "refreshInterval": &lt;spring.bpmn.ui.refresh-interval in milliseconds&gt; }
 * </pre>
 * The pages send all REST requests to {@code apiBasePath}; the UI has no other server-side endpoint. They offer the
 * upload of BPMN documents ({@code POST /process-definitions}) only if {@code deploymentEnabled} is {@code true}.
 * The header of the pages links to {@code logoutPath}; it is empty, and the link hidden, if
 * {@code spring.bpmn.ui.logout-path} is empty. The detail page of a running process instance reloads its data every
 * {@code refreshInterval} milliseconds; {@code 0} disables that.
 */
@RestController
class BpmnUiConfigController {

    private final BpmnRestProperties properties;

    private final BpmnUiProperties uiProperties;

    BpmnUiConfigController(final BpmnRestProperties properties, final BpmnUiProperties uiProperties) {
        this.properties = properties;
        this.uiProperties = uiProperties;
    }

    @GetMapping(BpmnUiAutoConfiguration.PATH + "/config.json")
    Map<String, Object> config(final HttpServletRequest request) {
        final String logoutPath = this.uiProperties.logoutPath();
        return Map.of(
                "apiBasePath", request.getContextPath() + this.properties.basePath(),
                "deploymentEnabled", this.properties.deploymentEnabled(),
                "logoutPath", logoutPath.isEmpty() ? "" : request.getContextPath() + logoutPath,
                "refreshInterval", this.uiProperties.refreshInterval().toMillis());
    }
}
