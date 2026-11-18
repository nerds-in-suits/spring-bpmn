package de.nemeses.spring.bpmn.exec.task;

import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import de.nemeses.spring.bpmn.exec.BpmnNodeImplementation;
import de.nemeses.spring.bpmn.exec.ConditionEvaluator;
import de.nemeses.spring.bpmn.exec.FlowSelector;
import de.nemeses.spring.bpmn.repository.ProcessInstanceRepository;

/**
 * Registers the invokers of this package and their helpers; imported by
 * {@link de.nemeses.spring.bpmn.ProcessDefinitionAutoConfiguration}. Each {@link Bean} method is named like the bean.
 */
@Configuration(proxyBeanMethods = false)
@SuppressWarnings("checkstyle:ClassDataAbstractionCoupling") // creates every bean of the package, by design
public class TaskInvokerConfiguration {

    @Bean
    ActivityLoop activityLoop(final ConditionEvaluator conditions) {
        return new ActivityLoop(conditions);
    }

    @Bean
    ManualTaskInvoker manualTaskInvoker(final TaskRunner runner, final FlowSelector flows) {
        return new ManualTaskInvoker(runner, flows);
    }

    @Bean
    ScriptTaskInvoker scriptTaskInvoker(final TaskRunner runner, final FlowSelector flows,
            final SpelScriptRunner scripts) {
        return new ScriptTaskInvoker(runner, flows, scripts);
    }

    /**
     * @param implementations the {@link BpmnNodeImplementation} beans by their bean name, which service tasks refer to
     */
    @Bean
    ServiceTaskInvoker serviceTaskInvoker(final TaskRunner runner, final FlowSelector flows,
            final Map<String, BpmnNodeImplementation> implementations) {
        return new ServiceTaskInvoker(runner, flows, implementations);
    }

    @Bean
    SpelScriptRunner spelScriptRunner() {
        return new SpelScriptRunner();
    }

    @Bean
    TaskInvoker taskInvoker(final TaskRunner runner, final FlowSelector flows) {
        return new TaskInvoker(runner, flows);
    }

    @Bean
    TaskRunner taskRunner(final ProcessInstanceRepository instances, final ActivityLoop loops) {
        return new TaskRunner(instances, loops);
    }

    @Bean
    UserTaskInvoker userTaskInvoker(final ProcessInstanceRepository instances, final FlowSelector flows) {
        return new UserTaskInvoker(instances, flows);
    }
}
