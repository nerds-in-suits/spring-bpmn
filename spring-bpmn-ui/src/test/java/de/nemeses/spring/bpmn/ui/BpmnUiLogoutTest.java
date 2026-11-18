package de.nemeses.spring.bpmn.ui;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletRequest;

import de.nemeses.spring.bpmn.rest.BpmnRestProperties;

/**
 * {@code spring.bpmn.ui.logout-path} in {@code config.json}: configured, empty (hides the link) and invalid.
 */
class BpmnUiLogoutTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(BpmnUiAutoConfiguration.class))
            .withUserConfiguration(RestProperties.class);

    @Test
    void testConfiguredLogoutPath() {
        this.runner.withPropertyValues("spring.bpmn.ui.logout-path=/sign-out")
                .run(context -> Assertions.assertThat(context.getBean(BpmnUiConfigController.class)
                        .config(request("/app")))
                        .containsEntry("logoutPath", "/app/sign-out"));
    }

    @Test
    void testEmptyLogoutPathHidesLink() {
        this.runner.withPropertyValues("spring.bpmn.ui.logout-path=")
                .run(context -> Assertions.assertThat(context.getBean(BpmnUiConfigController.class)
                        .config(request("/app")))
                        .containsEntry("logoutPath", ""));
    }

    @Test
    void testRelativeLogoutPathFails() {
        this.runner.withPropertyValues("spring.bpmn.ui.logout-path=logout")
                .run(context -> Assertions.assertThat(context).hasFailed());
    }

    private static MockHttpServletRequest request(final String contextPath) {
        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContextPath(contextPath);
        return request;
    }

    /**
     * Stands in for the REST auto-configuration, which enables {@link BpmnRestProperties}.
     */
    @Configuration
    @EnableConfigurationProperties(BpmnRestProperties.class)
    static class RestProperties {
    }
}
