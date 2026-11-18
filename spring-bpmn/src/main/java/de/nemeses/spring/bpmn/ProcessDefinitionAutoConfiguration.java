package de.nemeses.spring.bpmn;

import java.util.Collection;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.support.ResourcePatternResolver;

import de.nemeses.spring.bpmn.exec.ProcessEngineConfiguration;
import de.nemeses.spring.bpmn.exec.call.CallInvokerConfiguration;
import de.nemeses.spring.bpmn.exec.event.EventInvokerConfiguration;
import de.nemeses.spring.bpmn.exec.gateway.GatewayInvokerConfiguration;
import de.nemeses.spring.bpmn.exec.loop.LoopInvokerConfiguration;
import de.nemeses.spring.bpmn.exec.subprocess.SubProcessInvokerConfiguration;
import de.nemeses.spring.bpmn.exec.task.TaskInvokerConfiguration;
import de.nemeses.spring.bpmn.repository.ProcessDefinitionService;

/**
 * Registers the process definitions found on the classpath and in {@link ProcessDefinitionSource} beans on startup,
 * and the process engine that executes them.
 * <p>
 * The beans are registered explicitly, without a component scan, so the sub-packages of other modules (e.g.
 * {@code repository.jpa} of {@code spring-bpmn-repository-jpa}, {@code rest} of {@code spring-bpmn-rest}) are never
 * picked up; their auto-configurations register their beans in their order and under their own conditions.
 * <p>
 * The beans of this package are {@link Bean} methods of this class; the beans of {@code exec} and each of its
 * sub-packages are {@link Bean} methods of the configuration class of that package, imported here, because most of
 * them are package-private. Every {@code @Bean} method is named like the bean, so the bean names are the ones a
 * component scan would give, and the {@link de.nemeses.spring.bpmn.exec.BpmnNodeExecutor} keeps the bean name its
 * persisted tasks refer to.
 * <p>
 * A new node type is executed by a new {@link de.nemeses.spring.bpmn.exec.Invoker} bean: add a {@code @Bean} method
 * for it to the configuration class of its package (e.g. {@link TaskInvokerConfiguration} for {@code exec.task}); an
 * invoker in a new sub-package of {@code exec} needs a new configuration class there, added to the {@link Import} of
 * this class. The same applies to a new {@link de.nemeses.spring.bpmn.exec.InvokerEnhancer}.
 */
@AutoConfiguration
@Import({ ProcessEngineConfiguration.class, CallInvokerConfiguration.class, EventInvokerConfiguration.class,
    GatewayInvokerConfiguration.class, LoopInvokerConfiguration.class, SubProcessInvokerConfiguration.class,
    TaskInvokerConfiguration.class })
public class ProcessDefinitionAutoConfiguration {

    @Bean
    BpmnParser bpmnParser() throws BpmnParseException {
        return new BpmnParser();
    }

    @Bean
    ConfiguredLocationsProcessDefinitionSource configuredLocationsProcessDefinitionSource(
            final ResourcePatternResolver resolver,
            @Value("${spring.bpmn.process-definition-locations:classpath*:/process-definitions/*.bpmn}")
            final List<String> locations) {
        return new ConfiguredLocationsProcessDefinitionSource(resolver, locations);
    }

    @Bean
    ProcessDefinitionRegistrar processDefinitionRegistrar(final BpmnParser parser,
            final ProcessDefinitionService service, final Collection<ProcessDefinitionSource> sources) {
        return new ProcessDefinitionRegistrar(parser, service, sources);
    }
}
