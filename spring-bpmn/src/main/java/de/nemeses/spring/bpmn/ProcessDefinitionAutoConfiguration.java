package de.nemeses.spring.bpmn;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import de.nemeses.spring.bpmn.repository.ProcessDefinitionService;

/**
 * Registers the process definitions found on the classpath and in {@link ProcessDefinitionSource} beans on startup.
 */
@AutoConfiguration(afterName = "de.nemeses.spring.bpmn.repository.jpa.JpaBpmnRepositoryAutoConfiguration")
@ConditionalOnBean(ProcessDefinitionService.class)
@EnableConfigurationProperties(ProcessDefinitionProperties.class)
public class ProcessDefinitionAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    BpmnParser bpmnParser() throws BpmnParseException {
        return new BpmnParser();
    }

    @Bean
    @ConditionalOnMissingBean
    ProcessDefinitionRegistrar processDefinitionRegistrar(final BpmnParser parser,
            final ProcessDefinitionService service, final ApplicationContext context,
            final ProcessDefinitionProperties properties, final ObjectProvider<ProcessDefinitionSource> sources) {
        return new ProcessDefinitionRegistrar(parser, service, context, properties.processDefinitionLocations(),
                sources.orderedStream().toList());
    }

    @Bean
    ApplicationRunner processDefinitionRegistration(final ProcessDefinitionRegistrar registrar) {
        return args -> registrar.registerAll();
    }
}
