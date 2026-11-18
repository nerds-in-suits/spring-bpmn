package de.nemeses.spring.bpmn.exec.gateway;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import de.nemeses.spring.bpmn.exec.FlowSelector;

/**
 * Registers the invokers of this package; imported by
 * {@link de.nemeses.spring.bpmn.ProcessDefinitionAutoConfiguration}. Each {@link Bean} method is named like the bean.
 */
@Configuration(proxyBeanMethods = false)
public class GatewayInvokerConfiguration {

    @Bean
    ExclusiveGatewayInvoker exclusiveGatewayInvoker(final FlowSelector flows) {
        return new ExclusiveGatewayInvoker(flows);
    }

    @Bean
    ParallelGatewayInvoker parallelGatewayInvoker() {
        return new ParallelGatewayInvoker();
    }
}
