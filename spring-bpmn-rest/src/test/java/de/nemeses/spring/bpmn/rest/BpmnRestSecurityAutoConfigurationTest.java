package de.nemeses.spring.bpmn.rest;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;

import de.nemeses.spring.bpmn.BpmnParser;
import de.nemeses.spring.bpmn.exec.ProcessDefinitionModelCache;
import de.nemeses.spring.bpmn.exec.ProcessEngineService;
import de.nemeses.spring.bpmn.repository.ProcessDefinitionService;
import de.nemeses.spring.bpmn.repository.ProcessInstanceRepository;
import de.nemeses.spring.bpmn.repository.ProcessStepRepository;
import tools.jackson.databind.json.JsonMapper;

/**
 * Conditions of {@link BpmnRestSecurityConfiguration} and the fail-fast check of {@link BpmnRestAutoConfiguration}.
 */
@ExtendWith(OutputCaptureExtension.class)
class BpmnRestSecurityAutoConfigurationTest {

    private static final String OPT_OUT = "spring.bpmn.rest.security.enabled=false";

    private static final String BOOT_CHAIN = "defaultSecurityFilterChain";

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SecurityAutoConfiguration.class,
                    UserDetailsServiceAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class,
                    BpmnRestAutoConfiguration.class))
            .withUserConfiguration(Engine.class);

    @Test
    void testSecuredByDefault() {
        this.runner.run(context -> Assertions.assertThat(context)
                .hasNotFailed()
                .hasBean(BpmnRestSecurityConfiguration.CHAIN)
                .hasBean(BOOT_CHAIN));
    }

    @Test
    void testFailsWithoutSpringSecurity() {
        this.runner.withClassLoader(new FilteredClassLoader("org.springframework.security"))
                .run(context -> Assertions.assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .rootCause()
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("Spring Security is not on the classpath")
                        .hasMessageContaining(OPT_OUT));
    }

    @Test
    void testOptOutWithoutSpringSecurity(final CapturedOutput output) {
        this.runner.withClassLoader(new FilteredClassLoader("org.springframework.security"))
                .withPropertyValues(OPT_OUT)
                .run(context -> Assertions.assertThat(context)
                        .hasNotFailed()
                        .hasSingleBean(ProcessDefinitionController.class)
                        .doesNotHaveBean(BpmnRestSecurityConfiguration.CHAIN));
        Assertions.assertThat(output).contains("is not secured by spring-bpmn-rest");
    }

    @Test
    void testOptOutWithSpringSecurity() {
        this.runner.withPropertyValues(OPT_OUT)
                .run(context -> Assertions.assertThat(context)
                        .hasNotFailed()
                        .doesNotHaveBean(BpmnRestSecurityConfiguration.CHAIN)
                        .hasBean(BOOT_CHAIN));
    }

    @Test
    void testExplicitlyEnabled() {
        this.runner.withPropertyValues("spring.bpmn.rest.security.enabled=true")
                .run(context -> Assertions.assertThat(context).hasBean(BpmnRestSecurityConfiguration.CHAIN));
    }

    @Test
    void testReplacedByApplication() {
        this.runner.withUserConfiguration(OwnChain.class)
                .run(context -> Assertions.assertThat(context.getBean(BpmnRestSecurityConfiguration.CHAIN))
                        .isSameAs(context.getBean(OwnChain.class).chain));
    }

    @Test
    void testFailsWithoutWebSecurity() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(BpmnRestAutoConfiguration.class))
                .withUserConfiguration(Engine.class)
                .run(context -> Assertions.assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .rootCause()
                        .hasMessageContaining("web support is not enabled")
                        .hasMessageContaining(OPT_OUT));
    }

    /**
     * The mocked engine the REST auto-configuration needs.
     */
    @Configuration(proxyBeanMethods = false)
    static class Engine {

        @Bean
        ProcessEngineService processEngineService() {
            return Mockito.mock(ProcessEngineService.class);
        }

        @Bean
        ProcessDefinitionService processDefinitionService() {
            return Mockito.mock(ProcessDefinitionService.class);
        }

        @Bean
        ProcessInstanceRepository processInstanceRepository() {
            return Mockito.mock(ProcessInstanceRepository.class);
        }

        @Bean
        ProcessStepRepository processStepRepository() {
            return Mockito.mock(ProcessStepRepository.class);
        }

        @Bean
        BpmnParser bpmnParser() {
            return Mockito.mock(BpmnParser.class);
        }

        @Bean
        ProcessDefinitionModelCache processDefinitionModelCache() {
            return Mockito.mock(ProcessDefinitionModelCache.class);
        }

        @Bean
        JsonMapper jsonMapper() {
            return JsonMapper.builder().build();
        }
    }

    /**
     * An application chain that replaces the one of the module.
     */
    @Configuration(proxyBeanMethods = false)
    static class OwnChain {

        private final SecurityFilterChain chain = new DefaultSecurityFilterChain(AnyRequestMatcher.INSTANCE);

        @Bean(BpmnRestSecurityConfiguration.CHAIN)
        SecurityFilterChain bpmnRestSecurityFilterChain() {
            return this.chain;
        }
    }
}
