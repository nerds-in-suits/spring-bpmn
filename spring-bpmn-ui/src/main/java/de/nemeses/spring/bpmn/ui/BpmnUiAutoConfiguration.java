package de.nemeses.spring.bpmn.ui;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.autoconfigure.error.ErrorMvcAutoConfiguration;
import org.springframework.boot.webmvc.autoconfigure.error.ErrorViewResolver;
import org.springframework.context.annotation.Bean;
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
 * Boot, with Alpine.js and the bpmn-js viewer from their WebJars ({@code /webjars/alpinejs/...},
 * {@code /webjars/bpmn-js/...}). A document without diagram interchange (BPMN DI) is laid out in the browser by the
 * vendored ES module {@code vendor/bpmn-auto-layout/index.js} (bpmn-auto-layout 2.0.0-alpha.2, MIT, no WebJar of 2.x
 * exists), whose npm dependencies come from their WebJars through the import map of {@code index.html}.
 * This configuration only redirects
 * {@code /bpmn-ui} to {@code /bpmn-ui/}, forwards {@code /bpmn-ui/} to {@code index.html} and registers
 * {@link BpmnUiConfigController}, which tells the pages where the REST API is, and {@link BpmnUiErrorViewResolver},
 * which shows the UI's own error page instead of the "Whitelabel Error Page" below {@code /bpmn-ui}. The UI is not
 * secured by this module: the application's login (or Spring Boot's default one) protects {@code /bpmn-ui/}, and the
 * REST API of {@code spring-bpmn-rest} accepts that session.
 * The header links to the application's logout ({@link BpmnUiProperties#logoutPath()}).
 */
@AutoConfiguration(after = {BpmnRestAutoConfiguration.class, ErrorMvcAutoConfiguration.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnBean(BpmnRestProperties.class)
@EnableConfigurationProperties(BpmnUiProperties.class)
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

    /**
     * The error page of the UI below {@code /bpmn-ui}. This configuration runs after Spring Boot's
     * {@code ErrorMvcAutoConfiguration}, whose {@code DefaultErrorViewResolver} is only created if there is no other
     * {@link ErrorViewResolver}, so the application keeps it for all other paths.
     */
    @Bean
    ErrorViewResolver bpmnUiErrorViewResolver() {
        return new BpmnUiErrorViewResolver();
    }
}
