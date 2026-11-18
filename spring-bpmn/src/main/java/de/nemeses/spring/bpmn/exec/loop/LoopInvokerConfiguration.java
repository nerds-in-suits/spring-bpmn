package de.nemeses.spring.bpmn.exec.loop;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import de.nemeses.spring.bpmn.exec.ConditionEvaluator;
import de.nemeses.spring.bpmn.exec.FlowSelector;
import de.nemeses.spring.bpmn.repository.ProcessInstanceRepository;
import de.nemeses.spring.bpmn.repository.ProcessStepRepository;

/**
 * Registers the invoker enhancers of this package; imported by
 * {@link de.nemeses.spring.bpmn.ProcessDefinitionAutoConfiguration}. Each {@link Bean} method is named like the bean.
 */
@Configuration(proxyBeanMethods = false)
public class LoopInvokerConfiguration {

    @Bean
    MultiInstanceLoopEnhancer multiInstanceLoopEnhancer(final ProcessInstanceRepository instances,
            final ProcessStepRepository steps, final ConditionEvaluator conditions, final FlowSelector flows) {
        return new MultiInstanceLoopEnhancer(instances, steps, conditions, flows);
    }
}
