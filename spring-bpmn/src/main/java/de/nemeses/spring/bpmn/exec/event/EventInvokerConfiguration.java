package de.nemeses.spring.bpmn.exec.event;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import de.nemeses.spring.bpmn.repository.ProcessInstanceRepository;
import de.nemeses.spring.bpmn.repository.ProcessStepRepository;
import de.nemeses.spring.tasks.TaskService;

/**
 * Registers the invokers of this package; imported by
 * {@link de.nemeses.spring.bpmn.ProcessDefinitionAutoConfiguration}. Each {@link Bean} method is named like the bean.
 */
@Configuration(proxyBeanMethods = false)
public class EventInvokerConfiguration {

    @Bean
    EndEventInvoker endEventInvoker(final ProcessInstanceRepository instances, final ProcessStepRepository steps,
            final TaskService taskService) {
        return new EndEventInvoker(instances, steps, taskService);
    }

    @Bean
    StartEventInvoker startEventInvoker() {
        return new StartEventInvoker();
    }
}
