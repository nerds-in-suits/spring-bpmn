package de.nemeses.spring.bpmn.exec.call;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import de.nemeses.spring.bpmn.exec.FlowSelector;
import de.nemeses.spring.bpmn.exec.ProcessEngineService;
import de.nemeses.spring.bpmn.repository.ProcessDefinitionService;
import de.nemeses.spring.bpmn.repository.ProcessInstanceRepository;

/**
 * Registers the invokers of this package; imported by
 * {@link de.nemeses.spring.bpmn.ProcessDefinitionAutoConfiguration}. Each {@link Bean} method is named like the bean.
 */
@Configuration(proxyBeanMethods = false)
public class CallInvokerConfiguration {

    @Bean
    CallActivityInvoker callActivityInvoker(final ProcessDefinitionService definitions,
            final ProcessInstanceRepository instances, final ProcessEngineService engine, final FlowSelector flows) {
        return new CallActivityInvoker(definitions, instances, engine, flows);
    }
}
