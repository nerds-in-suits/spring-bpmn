package de.nemeses.spring.bpmn.rest;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import de.nemeses.spring.bpmn.ProcessDefinitionAutoConfiguration;
import de.nemeses.spring.bpmn.exec.ProcessEngineService;

/**
 * Registers the REST controllers of the BPMN engine in a servlet web application that has a
 * {@link ProcessEngineService}, and prefixes their paths with {@link BpmnRestProperties#basePath()}.
 * <p>
 * The endpoints are not secured by this module; the application protects the base path, e.g. with Spring Security.
 */
@AutoConfiguration(after = ProcessDefinitionAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnBean(ProcessEngineService.class)
@EnableConfigurationProperties(BpmnRestProperties.class)
@Import({ BpmnRestExceptionHandler.class, ProcessDefinitionController.class, ProcessInstanceController.class,
    ProcessNodes.class, UserTaskController.class })
public class BpmnRestAutoConfiguration implements WebMvcConfigurer {

    private final BpmnRestProperties properties;

    BpmnRestAutoConfiguration(final BpmnRestProperties properties) {
        this.properties = properties;
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
