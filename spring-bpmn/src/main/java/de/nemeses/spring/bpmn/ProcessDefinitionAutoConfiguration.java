package de.nemeses.spring.bpmn;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.stereotype.Component;

/**
 * Registers the process definitions found on the classpath and in {@link ProcessDefinitionSource} beans on startup.
 * <p>
 * Scans the components of the engine: this package and {@code exec} with its sub-packages. Other sub-packages belong
 * to other modules (e.g. {@code repository.jpa} of {@code spring-bpmn-repository-jpa}, {@code rest} of
 * {@code spring-bpmn-rest}) and are not scanned, because their auto-configurations register their beans in their
 * order and under their own conditions.
 */
@AutoConfiguration
@ComponentScan(basePackageClasses = ProcessDefinitionAutoConfiguration.class,
        useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(Component.class),
        excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX,
                pattern = "de\\.nemeses\\.spring\\.bpmn\\.(?!exec\\.)[a-z0-9_]+\\..*"))
public class ProcessDefinitionAutoConfiguration {
}
