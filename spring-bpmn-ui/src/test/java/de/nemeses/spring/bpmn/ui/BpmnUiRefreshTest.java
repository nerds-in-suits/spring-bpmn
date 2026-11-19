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
 * {@code spring.bpmn.ui.refresh-interval} in {@code config.json} in milliseconds: default, configured, disabled and
 * invalid.
 */
class BpmnUiRefreshTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(BpmnUiAutoConfiguration.class))
            .withUserConfiguration(RestProperties.class);

    @Test
    void testDefaultRefreshInterval() {
        this.runner.run(context -> Assertions.assertThat(context.getBean(BpmnUiConfigController.class)
                .config(new MockHttpServletRequest()))
                .containsEntry("refreshInterval", 10_000L));
    }

    @Test
    void testConfiguredRefreshInterval() {
        this.runner.withPropertyValues("spring.bpmn.ui.refresh-interval=2500ms")
                .run(context -> Assertions.assertThat(context.getBean(BpmnUiConfigController.class)
                        .config(new MockHttpServletRequest()))
                        .containsEntry("refreshInterval", 2_500L));
    }

    @Test
    void testZeroRefreshIntervalDisablesRefresh() {
        this.runner.withPropertyValues("spring.bpmn.ui.refresh-interval=0")
                .run(context -> Assertions.assertThat(context.getBean(BpmnUiConfigController.class)
                        .config(new MockHttpServletRequest()))
                        .containsEntry("refreshInterval", 0L));
    }

    @Test
    void testNegativeRefreshIntervalFails() {
        this.runner.withPropertyValues("spring.bpmn.ui.refresh-interval=-1s")
                .run(context -> Assertions.assertThat(context).hasFailed());
    }

    /**
     * Stands in for the REST auto-configuration, which enables {@link BpmnRestProperties}.
     */
    @Configuration
    @EnableConfigurationProperties(BpmnRestProperties.class)
    static class RestProperties {
    }
}
