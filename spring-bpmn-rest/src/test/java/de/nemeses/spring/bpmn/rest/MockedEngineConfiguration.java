package de.nemeses.spring.bpmn.rest;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import de.nemeses.spring.bpmn.BpmnParseException;
import de.nemeses.spring.bpmn.BpmnParser;
import de.nemeses.spring.bpmn.exec.ProcessDefinitionModelCache;
import de.nemeses.spring.bpmn.exec.ProcessEngineException;
import de.nemeses.spring.bpmn.exec.ProcessEngineService;
import de.nemeses.spring.bpmn.repository.ProcessDefinition;
import de.nemeses.spring.bpmn.repository.ProcessDefinitionService;
import de.nemeses.spring.bpmn.repository.ProcessInstance;
import de.nemeses.spring.bpmn.repository.ProcessInstanceRepository;
import de.nemeses.spring.bpmn.repository.ProcessStep;
import de.nemeses.spring.bpmn.repository.ProcessStepRepository;

/**
 * Test application of the controller tests: the REST auto-configuration on mocked engine services and repositories,
 * a real {@link BpmnParser} and a model cache that parses with it.
 */
@Configuration
@EnableAutoConfiguration(excludeName = { "de.nemeses.spring.bpmn.ProcessDefinitionAutoConfiguration",
    "de.nemeses.spring.bpmn.repository.jpa.JpaBpmnRepositoryAutoConfiguration",
    "de.nemeses.spring.tasks.jpa.JpaTaskPersistenceAutoConfiguration" })
class MockedEngineConfiguration {

    /** Process {@code order}: start event {@code start}, user task {@code approve}, end event {@code end}. */
    static final String ORDER_XML = "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" id=\"d\""
            + " targetNamespace=\"urn:t\"><process id=\"order\"><startEvent id=\"start\"/>"
            + "<userTask id=\"approve\" name=\"Approve order\"/><endEvent id=\"end\"/>"
            + "<sequenceFlow id=\"f1\" sourceRef=\"start\" targetRef=\"approve\"/>"
            + "<sequenceFlow id=\"f2\" sourceRef=\"approve\" targetRef=\"end\"/></process></definitions>";

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
    BpmnParser bpmnParser() throws BpmnParseException {
        return new BpmnParser();
    }

    @Bean
    ProcessDefinitionModelCache processDefinitionModelCache(final BpmnParser parser) throws ProcessEngineException {
        final ProcessDefinitionModelCache models = Mockito.mock(ProcessDefinitionModelCache.class);
        Mockito.when(models.document(ArgumentMatchers.any())).thenAnswer(invocation -> parser.parse(
                new ByteArrayInputStream(invocation.<ProcessDefinition>getArgument(0).bpmnXml()
                        .getBytes(StandardCharsets.UTF_8))));
        return models;
    }

    /**
     * A process definition version.
     *
     * @param name the name
     * @param createdDate the creation date
     * @param bpmnXml the BPMN XML
     */
    record Definition(String name, Date createdDate, String bpmnXml) implements ProcessDefinition {
    }

    /**
     * A process instance.
     *
     * @param id the id
     * @param processDefinition the definition
     * @param variables the variables
     * @param startDate the start date
     * @param endDate the end date
     * @param parentStep the parent step
     * @param parentInstance the parent instance
     */
    record Instance(
            UUID id,
            ProcessDefinition processDefinition,
            Map<String, Object> variables,
            Date startDate,
            Date endDate,
            ProcessStep parentStep,
            ProcessInstance parentInstance) implements ProcessInstance {
    }

    /**
     * A process step.
     *
     * @param id the id
     * @param processInstanceId the instance id
     * @param nodeId the node id
     * @param incomingFlowId the incoming flow
     * @param parentStepId the parent step id
     * @param loopCounter the loop counter
     * @param loopItem the loop item
     * @param startDate the start date
     * @param endDate the end date
     */
    record Step(
            UUID id,
            UUID processInstanceId,
            String nodeId,
            String incomingFlowId,
            UUID parentStepId,
            Integer loopCounter,
            Object loopItem,
            Date startDate,
            Date endDate) implements ProcessStep {

        /**
         * @param processInstanceId the instance id
         * @param nodeId the node id
         * @param start the start date in milliseconds
         * @param end the end date in milliseconds, {@code null} if pending
         */
        Step(final UUID processInstanceId, final String nodeId, final long start, final Long end) {
            this(
                    UUID.randomUUID(),
                    processInstanceId,
                    nodeId,
                    null,
                    null,
                    null,
                    null,
                    new Date(start),
                    end == null ? null : new Date(end));
        }
    }
}
