package de.nemeses.spring.bpmn.ui;

import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import de.nemeses.spring.bpmn.BpmnParser;
import de.nemeses.spring.bpmn.exec.ProcessDefinitionModelCache;
import de.nemeses.spring.bpmn.exec.ProcessEngineService;
import de.nemeses.spring.bpmn.repository.ProcessDefinitionService;
import de.nemeses.spring.bpmn.repository.ProcessInstanceRepository;
import de.nemeses.spring.bpmn.repository.ProcessStepRepository;

/**
 * Test application: the REST and UI auto-configurations on a mocked engine.
 */
@Configuration
@EnableAutoConfiguration(excludeName = "de.nemeses.spring.bpmn.ProcessDefinitionAutoConfiguration")
class UiTestConfiguration {

    @Bean
    ProcessEngineService processEngineService() {
        return Mockito.mock(ProcessEngineService.class);
    }

    @Bean
    ProcessDefinitionService processDefinitionService() {
        return Mockito.mock(ProcessDefinitionService.class);
    }

    @Bean
    ProcessInstanceRepository processInstanceRepository() {
        return Mockito.mock(ProcessInstanceRepository.class);
    }

    @Bean
    ProcessStepRepository processStepRepository() {
        return Mockito.mock(ProcessStepRepository.class);
    }

    @Bean
    BpmnParser bpmnParser() {
        return Mockito.mock(BpmnParser.class);
    }

    @Bean
    ProcessDefinitionModelCache processDefinitionModelCache() {
        return Mockito.mock(ProcessDefinitionModelCache.class);
    }
}
