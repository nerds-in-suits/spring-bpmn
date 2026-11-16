package de.nemeses.spring.bpmn.ui;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import de.nemeses.spring.bpmn.rest.BpmnRestProperties;

/**
 * Conditions of {@link BpmnUiAutoConfiguration}.
 */
class BpmnUiAutoConfigurationTest {

    private static final AutoConfigurations UI = AutoConfigurations.of(BpmnUiAutoConfiguration.class);

    @Test
    void testActiveWithRestApi() {
        new WebApplicationContextRunner()
                .withConfiguration(UI)
                .withUserConfiguration(RestProperties.class)
                .run(context -> Assertions.assertThat(context)
                        .hasSingleBean(BpmnUiAutoConfiguration.class)
                        .hasSingleBean(BpmnUiConfigController.class));
    }

    @Test
    void testInactiveWithoutRestApi() {
        new WebApplicationContextRunner()
                .withConfiguration(UI)
                .run(context -> Assertions.assertThat(context)
                        .doesNotHaveBean(BpmnUiAutoConfiguration.class)
                        .doesNotHaveBean(BpmnUiConfigController.class));
    }

    @Test
    void testInactiveWithoutWebApplication() {
        new ApplicationContextRunner()
                .withConfiguration(UI)
                .withUserConfiguration(RestProperties.class)
                .run(context -> Assertions.assertThat(context)
                        .doesNotHaveBean(BpmnUiAutoConfiguration.class));
    }

    /**
     * Stands in for the REST auto-configuration, which enables {@link BpmnRestProperties}.
     */
    @Configuration
    @EnableConfigurationProperties(BpmnRestProperties.class)
    static class RestProperties {
    }
}
