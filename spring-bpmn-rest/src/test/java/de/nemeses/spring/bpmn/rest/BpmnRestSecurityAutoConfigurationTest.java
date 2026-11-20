package de.nemeses.spring.bpmn.rest;

import java.util.Arrays;
import java.util.stream.Stream;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;

import de.nemeses.spring.bpmn.ProcessDefinitionAutoConfiguration;
import de.nemeses.spring.bpmn.repository.jpa.JpaBpmnRepositoryAutoConfiguration;
import de.nemeses.spring.tasks.TaskConfiguration;
import de.nemeses.spring.tasks.jpa.JpaTaskPersistenceAutoConfiguration;

/**
 * Conditions of {@link BpmnRestSecurityConfiguration} and the fail-fast check of {@link BpmnRestAutoConfiguration}.
 */
@ExtendWith(OutputCaptureExtension.class)
class BpmnRestSecurityAutoConfigurationTest {

    private static final String OPT_OUT = "spring.bpmn.rest.security.enabled=false";

    private static final String BOOT_CHAIN = "defaultSecurityFilterChain";

    /** The task service schedules, but runs no tasks: the contexts only start. */
    private static final String NO_WORKERS = "spring.tasks.pool-size=0";

    /**
     * The auto-configurations of the real engine the REST auto-configuration needs, with the JPA repositories and the
     * JPA task persistence on H2.
     */
    private static final Class<?>[] ENGINE = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
        TransactionAutoConfiguration.class, JacksonAutoConfiguration.class, JpaBpmnRepositoryAutoConfiguration.class,
        JpaTaskPersistenceAutoConfiguration.class, ProcessDefinitionAutoConfiguration.class};

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(BpmnRestSecurityAutoConfigurationTest.withEngine(SecurityAutoConfiguration.class,
                    UserDetailsServiceAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class,
                    BpmnRestAutoConfiguration.class))
            .withUserConfiguration(TaskConfiguration.class)
            .withPropertyValues(NO_WORKERS);

    /**
     * @param others further auto-configurations
     * @return the auto-configurations of the engine and the others
     */
    private static AutoConfigurations withEngine(final Class<?>... others) {
        return AutoConfigurations.of(Stream.concat(Arrays.stream(ENGINE), Arrays.stream(others))
                .toArray(Class<?>[]::new));
    }

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
                .withConfiguration(BpmnRestSecurityAutoConfigurationTest.withEngine(BpmnRestAutoConfiguration.class))
                .withUserConfiguration(TaskConfiguration.class)
                .withPropertyValues(NO_WORKERS)
                .run(context -> Assertions.assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .rootCause()
                        .hasMessageContaining("web support is not enabled")
                        .hasMessageContaining(OPT_OUT));
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
