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
 * { "apiBasePath": "&lt;context path&gt;&lt;spring.bpmn.rest.base-path&gt;" }
 * </pre>
 * The pages send all REST requests to {@code apiBasePath}; the UI has no other server-side endpoint.
 */
@RestController
class BpmnUiConfigController {

    private final BpmnRestProperties properties;

    BpmnUiConfigController(final BpmnRestProperties properties) {
        this.properties = properties;
    }

    @GetMapping(BpmnUiAutoConfiguration.PATH + "/config.json")
    Map<String, String> config(final HttpServletRequest request) {
        return Map.of("apiBasePath", request.getContextPath() + this.properties.basePath());
    }
}
