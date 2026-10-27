package de.nemeses.spring.bpmn;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.stereotype.Component;

/**
 * Registers the process definitions found on the classpath and in {@link ProcessDefinitionSource} beans on startup.
 */
@AutoConfiguration
@ComponentScan(basePackageClasses = ProcessDefinitionAutoConfiguration.class,
        useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(Component.class))
public class ProcessDefinitionAutoConfiguration {
}
