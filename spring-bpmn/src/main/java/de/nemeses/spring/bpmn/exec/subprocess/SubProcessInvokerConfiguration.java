package de.nemeses.spring.bpmn.exec.subprocess;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import de.nemeses.spring.bpmn.exec.FlowSelector;
import de.nemeses.spring.bpmn.repository.ProcessStepRepository;

/**
 * Registers the invokers of this package; imported by
 * {@link de.nemeses.spring.bpmn.ProcessDefinitionAutoConfiguration}. Each {@link Bean} method is named like the bean.
 */
@Configuration(proxyBeanMethods = false)
public class SubProcessInvokerConfiguration {

    @Bean
    SubProcessInvoker subProcessInvoker(final ProcessStepRepository steps, final FlowSelector flows) {
        return new SubProcessInvoker(steps, flows);
    }
}
