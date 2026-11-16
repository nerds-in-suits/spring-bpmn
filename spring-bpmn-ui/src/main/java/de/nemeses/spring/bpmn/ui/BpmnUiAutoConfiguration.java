package de.nemeses.spring.bpmn.ui;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import de.nemeses.spring.bpmn.rest.BpmnRestAutoConfiguration;
import de.nemeses.spring.bpmn.rest.BpmnRestProperties;

/**
 * Makes the BPMN UI reachable at {@code /bpmn-ui/} in a servlet web application that has the REST API of
 * {@code spring-bpmn-rest} ({@link BpmnRestProperties}).
 * <p>
 * The pages themselves are static resources of this JAR in {@code META-INF/resources/bpmn-ui/}, served by Spring
 * Boot, with Alpine.js from its WebJar ({@code /webjars/alpinejs/...}). This configuration only redirects
 * {@code /bpmn-ui} to {@code /bpmn-ui/}, forwards {@code /bpmn-ui/} to {@code index.html} and registers
 * {@link BpmnUiConfigController}, which tells the pages where the REST API is. Like the REST API, the UI is not
 * secured by this module.
 */
@AutoConfiguration(after = BpmnRestAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnBean(BpmnRestProperties.class)
@Import(BpmnUiConfigController.class)
public class BpmnUiAutoConfiguration implements WebMvcConfigurer {

    /** The path of the UI, where its static resources are. */
    static final String PATH = "/bpmn-ui";

    /**
     * Redirects {@code /bpmn-ui} to {@code /bpmn-ui/}, so the relative links of the pages resolve, and forwards
     * {@code /bpmn-ui/} to the entry page.
     */
    @Override
    public void addViewControllers(final ViewControllerRegistry registry) {
        registry.addRedirectViewController(PATH, PATH + "/");
        registry.addViewController(PATH + "/").setViewName("forward:" + PATH + "/index.html");
    }
}
