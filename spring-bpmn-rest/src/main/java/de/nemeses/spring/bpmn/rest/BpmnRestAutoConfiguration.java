package de.nemeses.spring.bpmn.rest;

import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.ClassUtils;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import de.nemeses.spring.bpmn.ProcessDefinitionAutoConfiguration;
import de.nemeses.spring.bpmn.exec.ProcessEngineService;

/**
 * Registers the REST controllers of the BPMN engine in a servlet web application that has a
 * {@link ProcessEngineService}, and prefixes their paths with {@link BpmnRestProperties#basePath()}.
 * <p>
 * The endpoints are secured by {@link BpmnRestSecurityConfiguration} with Spring Security. The application must
 * either have Spring Security on the classpath or opt out explicitly with
 * {@code spring.bpmn.rest.security.enabled=false}; otherwise this configuration fails the startup, so the API is never
 * open by accident. With the opt-out, the module does not secure the endpoints and logs a warning.
 * <p>
 * Runs after Spring Boot's servlet web security auto-configuration, so Boot's default security filter chain is still
 * created when the application has none of its own.
 */
@AutoConfiguration(after = ProcessDefinitionAutoConfiguration.class,
        afterName = "org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnBean(ProcessEngineService.class)
@EnableConfigurationProperties(BpmnRestProperties.class)
@Import({ BpmnRestExceptionHandler.class, BpmnRestSecurityConfiguration.class, ProcessDefinitionController.class,
    ProcessInstanceController.class, ProcessNodes.class, ProcessVariableController.class, UserTaskController.class })
public class BpmnRestAutoConfiguration implements WebMvcConfigurer {

    /** A class of Spring Security's web support, whose presence enables {@link BpmnRestSecurityConfiguration}. */
    static final String SECURITY_CLASS = "org.springframework.security.web.SecurityFilterChain";

    private final BpmnRestProperties properties;

    /**
     * @param properties the properties
     * @param resourceLoader the class loader of the application, to look for Spring Security
     * @throws IllegalStateException if security is enabled but Spring Security is not on the classpath
     */
    BpmnRestAutoConfiguration(final BpmnRestProperties properties, final ResourceLoader resourceLoader) {
        this.properties = properties;
        if (!properties.security().enabled()) {
            LoggerFactory.getLogger(this.getClass()).warn("spring.bpmn.rest.security.enabled=false: the BPMN REST API"
                    + " below {} is not secured by spring-bpmn-rest", properties.basePath());
        } else if (!ClassUtils.isPresent(SECURITY_CLASS, resourceLoader.getClassLoader())) {
            throw new IllegalStateException("The BPMN REST API below " + properties.basePath() + " must be secured,"
                    + " but Spring Security is not on the classpath: add spring-boot-starter-security, or set"
                    + " spring.bpmn.rest.security.enabled=false explicitly to run the API without security");
        }
    }

    /**
     * Prefixes the paths of the controllers of this package with the base path.
     */
    @Override
    public void configurePathMatch(final PathMatchConfigurer configurer) {
        configurer.addPathPrefix(this.properties.basePath(),
                HandlerTypePredicate.forBasePackageClass(BpmnRestAutoConfiguration.class));
    }
}
