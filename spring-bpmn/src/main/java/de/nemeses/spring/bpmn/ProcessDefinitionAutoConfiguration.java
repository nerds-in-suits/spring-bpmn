package de.nemeses.spring.bpmn;

import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.support.ResourcePatternResolver;

import de.nemeses.spring.bpmn.repository.ProcessDefinitionService;

/**
 * Registers the process definitions found on the classpath and in {@link ProcessDefinitionSource} beans on startup.
 */
@AutoConfiguration(afterName = "de.nemeses.spring.bpmn.repository.jpa.JpaBpmnRepositoryAutoConfiguration")
@ConditionalOnBean(ProcessDefinitionService.class)
public class ProcessDefinitionAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    BpmnParser bpmnParser() throws BpmnParseException {
        return new BpmnParser();
    }

    @Bean
    ProcessDefinitionSource configuredLocationsProcessDefinitionSource(final ResourcePatternResolver resolver,
            @Value("${spring.bpmn.process-definition-locations:classpath*:/process-definitions/*.bpmn}")
            final List<String> locations) {
        return new ConfiguredLocationsProcessDefinitionSource(resolver, locations);
    }

    @Bean
    @ConditionalOnMissingBean
    ProcessDefinitionRegistrar processDefinitionRegistrar(final BpmnParser parser,
            final ProcessDefinitionService service, final ObjectProvider<ProcessDefinitionSource> sources) {
        return new ProcessDefinitionRegistrar(parser, service, sources.orderedStream().toList());
    }

    @Bean
    ApplicationRunner processDefinitionRegistration(final ProcessDefinitionRegistrar registrar) {
        return args -> registrar.registerAll();
    }
}
