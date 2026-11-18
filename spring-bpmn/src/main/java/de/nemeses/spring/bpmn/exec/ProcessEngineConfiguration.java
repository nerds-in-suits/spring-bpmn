package de.nemeses.spring.bpmn.exec;

import java.util.Collection;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import de.nemeses.spring.bpmn.BpmnParser;
import de.nemeses.spring.bpmn.repository.ProcessDefinitionService;
import de.nemeses.spring.bpmn.repository.ProcessInstanceRepository;
import de.nemeses.spring.bpmn.repository.ProcessStepRepository;
import de.nemeses.spring.tasks.TaskService;

/**
 * Registers the beans of the process engine in this package; imported by
 * {@link de.nemeses.spring.bpmn.ProcessDefinitionAutoConfiguration}, which also imports the invokers of the
 * sub-packages. Each {@link Bean} method is named like the bean.
 */
@Configuration(proxyBeanMethods = false)
public class ProcessEngineConfiguration {

    @Bean(BpmnNodeExecutor.BEAN_NAME)
    BpmnNodeExecutor bpmnNodeTask(final ProcessInstanceRepository instances, final ProcessStepRepository steps,
            final ProcessDefinitionModelCache models, final SubProcessScopes subProcesses,
            final InvokerRegistry invokers) {
        return new BpmnNodeExecutor(instances, steps, models, subProcesses, invokers);
    }

    @Bean
    ConditionEvaluator conditionEvaluator() {
        return new ConditionEvaluator();
    }

    @Bean
    FlowSelector flowSelector(final ConditionEvaluator conditions) {
        return new FlowSelector(conditions);
    }

    @Bean
    InvokerRegistry invokerRegistry(final ProcessInstanceRepository instances, final ProcessStepRepository steps,
            final TaskService taskService, final ProcessDefinitionModelCache models,
            final Collection<Invoker<?>> invokers, final Collection<InvokerEnhancer> enhancers) {
        return new InvokerRegistry(instances, steps, taskService, models, invokers, enhancers);
    }

    @Bean
    ProcessDefinitionModelCache processDefinitionModelCache(final BpmnParser parser) {
        return new ProcessDefinitionModelCache(parser);
    }

    @Bean
    ProcessEngineService processEngineService(final ProcessDefinitionService definitions,
            final ProcessInstanceRepository instances, final ProcessStepRepository steps,
            final ProcessDefinitionModelCache models, final ObjectProvider<InvokerRegistry> invokers,
            final ObjectProvider<BpmnNodeExecutor> executor) {
        return new ProcessEngineService(definitions, instances, steps, models, invokers, executor);
    }

    @Bean
    SubProcessScopes subProcessScopes(final ProcessStepRepository steps, final TaskService taskService,
            final InvokerRegistry invokers) {
        return new SubProcessScopes(steps, taskService, invokers);
    }
}
