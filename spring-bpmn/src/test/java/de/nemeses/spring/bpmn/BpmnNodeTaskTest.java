package de.nemeses.spring.bpmn;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.ContextConsumer;

import de.nemeses.spring.bpmn.repository.ProcessDefinition;
import de.nemeses.spring.bpmn.repository.ProcessDefinitionService;
import de.nemeses.spring.bpmn.repository.ProcessInstance;
import de.nemeses.spring.bpmn.repository.ProcessInstanceRepository;
import de.nemeses.spring.tasks.TaskConfiguration;
import de.nemeses.spring.tasks.TaskException;
import de.nemeses.spring.tasks.TaskPersistence;

/**
 * Executes single nodes through the {@link BpmnNodeTask} with an in-memory repository and recorded tasks.
 */
class BpmnNodeTaskTest {

    private static final String SPRING_NS = "xmlns:spring=\"" + BpmnNodeExecutor.NAMESPACE + "\" ";

    private final InMemoryProcessInstanceRepository instances = new InMemoryProcessInstanceRepository();
    private final RecordingTaskPersistence tasks = new RecordingTaskPersistence();
    private final List<BpmnNodeContext> calls = new ArrayList<>();

    private ProcessInstance instance(final String processContent) {
        final String bpmnXml = "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" " + SPRING_NS
                + "id=\"d\" targetNamespace=\"urn:t\"><process id=\"p\">" + processContent + "</process></definitions>";
        return this.instances.create(new ProcessDefinition() {
            @Override
            public String name() {
                return "p";
            }

            @Override
            public Date createdDate() {
                return new Date();
            }

            @Override
            public String bpmnXml() {
                return bpmnXml;
            }
        }, Map.of("amount", 3));
    }

    private void withEngine(final ContextConsumer<AssertableApplicationContext> test) {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ProcessDefinitionAutoConfiguration.class))
                .withUserConfiguration(TaskConfiguration.class)
                .withBean(ProcessDefinitionService.class, () -> new ProcessEngineServiceTest.MapDefinitionService(Map.of()))
                .withBean(ProcessInstanceRepository.class, () -> this.instances)
                .withBean(TaskPersistence.class, () -> this.tasks)
                .withBean("recording", BpmnNodeImplementation.class, () -> this.calls::add)
                .withBean("failing", BpmnNodeImplementation.class, () -> context -> {
                    throw new ProcessEngineException("failed");
                })
                .withBean("writing", BpmnNodeImplementation.class, () -> context -> {
                    context.setVariable("result", "ok");
                    context.removeVariable("amount");
                    context.removeVariable("missing");
                    this.calls.add(context);
                })
                .withBean("writingThenFailing", BpmnNodeImplementation.class, () -> context -> {
                    context.setVariable("result", "ok");
                    throw new ProcessEngineException("failed");
                })
                .run(test);
    }

    private static void execute(final AssertableApplicationContext context, final ProcessInstance instance,
            final String nodeId) throws TaskException {
        context.getBean(BpmnNodeTask.BEAN_NAME, BpmnNodeTask.class).execute(new NodeTaskPayload(instance.id(), nodeId));
    }

    private static void assertFails(final AssertableApplicationContext context, final ProcessInstance instance,
            final String nodeId) {
        final TaskException e = Assertions.assertThrows(TaskException.class,
                () -> BpmnNodeTaskTest.execute(context, instance, nodeId));
        Assertions.assertInstanceOf(ProcessEngineException.class, e.getCause());
    }

    private List<NodeTaskPayload> triggered(final AssertableApplicationContext context) {
        final BpmnNodeTask nodeTask = context.getBean(BpmnNodeTask.BEAN_NAME, BpmnNodeTask.class);
        return this.tasks.persisted().stream().map(task -> nodeTask.deserializePayload(task.payload())).toList();
    }

    private List<String> triggeredNodes(final AssertableApplicationContext context) {
        return this.triggered(context).stream().map(NodeTaskPayload::nodeId).toList();
    }

    private Date endDate(final ProcessInstance instance) {
        return this.instances.created().stream().filter(i -> i.id().equals(instance.id())).findFirst().orElseThrow()
                .endDate();
    }

    @Test
    void startTaskAndEndRunThroughProcess() {
        final ProcessInstance instance = this.instance("<startEvent id=\"s\"/><task id=\"t\"/><endEvent id=\"e\"/>"
                + "<sequenceFlow id=\"f1\" sourceRef=\"s\" targetRef=\"t\"/>"
                + "<sequenceFlow id=\"f2\" sourceRef=\"t\" targetRef=\"e\"/>");
        this.withEngine(context -> {
            BpmnNodeTaskTest.execute(context, instance, "s");
            Assertions.assertEquals(List.of(new NodeTaskPayload(instance.id(), "t")), this.triggered(context));
            Assertions.assertNull(this.endDate(instance));

            BpmnNodeTaskTest.execute(context, instance, "t");
            Assertions.assertEquals(List.of("t", "e"), this.triggeredNodes(context));

            BpmnNodeTaskTest.execute(context, instance, "e");
            Assertions.assertEquals(List.of("t", "e"), this.triggeredNodes(context));
            Assertions.assertNotNull(this.endDate(instance));
        });
    }

    @Test
    void nodeOfEndedInstanceIsSkipped() {
        final ProcessInstance instance = this.instance("<task id=\"t\"/><endEvent id=\"e\"/><endEvent id=\"e2\"/>"
                + "<sequenceFlow id=\"f\" sourceRef=\"t\" targetRef=\"e2\"/>");
        this.withEngine(context -> {
            BpmnNodeTaskTest.execute(context, instance, "e");
            final Date endDate = this.endDate(instance);

            BpmnNodeTaskTest.execute(context, instance, "t");
            BpmnNodeTaskTest.execute(context, instance, "e2");
            Assertions.assertTrue(this.tasks.persisted().isEmpty());
            Assertions.assertSame(endDate, this.endDate(instance));
        });
    }

    @Test
    void endEventWithEventDefinitionFails() {
        final ProcessInstance instance = this.instance("<endEvent id=\"e\"><terminateEventDefinition/></endEvent>");
        this.withEngine(context -> {
            BpmnNodeTaskTest.assertFails(context, instance, "e");
            Assertions.assertNull(this.endDate(instance));
        });
    }

    @Test
    void serviceTaskCallsImplementationWithVariables() {
        final ProcessInstance instance = this.instance("<serviceTask id=\"st\" spring:bean=\"recording\"/>"
                + "<endEvent id=\"e\"/><sequenceFlow id=\"f\" sourceRef=\"st\" targetRef=\"e\"/>");
        this.withEngine(context -> {
            BpmnNodeTaskTest.execute(context, instance, "st");

            Assertions.assertEquals(1, this.calls.size());
            final BpmnNodeContext call = this.calls.get(0);
            Assertions.assertEquals(instance.id(), call.processInstanceId());
            Assertions.assertEquals("st", call.nodeId());
            Assertions.assertEquals(Map.of("amount", 3), call.variables());
            Assertions.assertThrows(UnsupportedOperationException.class, () -> call.variables().put("x", 1));
            Assertions.assertEquals(List.of("e"), this.triggeredNodes(context));
        });
    }

    @Test
    void serviceTaskStoresChangedVariables() {
        final ProcessInstance instance = this.instance("<serviceTask id=\"st\" spring:bean=\"writing\"/>"
                + "<endEvent id=\"e\"/><sequenceFlow id=\"f\" sourceRef=\"st\" targetRef=\"e\"/>");
        this.withEngine(context -> {
            BpmnNodeTaskTest.execute(context, instance, "st");

            Assertions.assertEquals(Map.of("result", "ok"), this.calls.get(0).variables());
            Assertions.assertEquals(Map.of("result", "ok"), this.instances.retrieve(instance.id()).variables());
            Assertions.assertEquals(List.of("e"), this.triggeredNodes(context));
        });
    }

    @Test
    void serviceTaskRejectsNullVariable() {
        final ProcessInstance instance = this.instance("<serviceTask id=\"st\" spring:bean=\"recording\"/>");
        this.withEngine(context -> {
            BpmnNodeTaskTest.execute(context, instance, "st");

            final BpmnNodeContext call = this.calls.get(0);
            Assertions.assertThrows(NullPointerException.class, () -> call.setVariable("x", null));
            Assertions.assertThrows(NullPointerException.class, () -> call.setVariable(null, 1));
        });
    }

    @Test
    void failingServiceTaskDiscardsChangedVariables() {
        final ProcessInstance instance = this.instance("<serviceTask id=\"st\" spring:bean=\"writingThenFailing\"/>"
                + "<endEvent id=\"e\"/><sequenceFlow id=\"f\" sourceRef=\"st\" targetRef=\"e\"/>");
        this.withEngine(context -> {
            BpmnNodeTaskTest.assertFails(context, instance, "st");

            Assertions.assertEquals(Map.of("amount", 3), this.instances.retrieve(instance.id()).variables());
            Assertions.assertTrue(this.tasks.persisted().isEmpty());
        });
    }

    @Test
    void failingServiceTaskTakesNoFlow() {
        final ProcessInstance instance = this.instance("<serviceTask id=\"st\" spring:bean=\"failing\"/>"
                + "<endEvent id=\"e\"/><sequenceFlow id=\"f\" sourceRef=\"st\" targetRef=\"e\"/>");
        this.withEngine(context -> {
            final TaskException e = Assertions.assertThrows(TaskException.class,
                    () -> BpmnNodeTaskTest.execute(context, instance, "st"));
            Assertions.assertEquals("failed", e.getCause().getMessage());
            Assertions.assertTrue(this.tasks.persisted().isEmpty());
        });
    }

    @Test
    void serviceTaskWithoutOrWithUnknownBeanFails() {
        final ProcessInstance instance = this.instance("<serviceTask id=\"none\"/>"
                + "<serviceTask id=\"unknown\" spring:bean=\"missing\"/>"
                + "<serviceTask id=\"wrongType\" spring:bean=\"" + BpmnNodeTask.BEAN_NAME + "\"/>");
        this.withEngine(context -> {
            BpmnNodeTaskTest.assertFails(context, instance, "none");
            BpmnNodeTaskTest.assertFails(context, instance, "unknown");
            BpmnNodeTaskTest.assertFails(context, instance, "wrongType");
        });
    }

    @Test
    void exclusiveGatewayTakesDefaultFlow() {
        final ProcessInstance instance = this.instance("<exclusiveGateway id=\"g\" default=\"f2\"/>"
                + "<task id=\"a\"/><task id=\"b\"/>"
                + "<sequenceFlow id=\"f1\" sourceRef=\"g\" targetRef=\"a\"><conditionExpression>${ok}</conditionExpression>"
                + "</sequenceFlow><sequenceFlow id=\"f2\" sourceRef=\"g\" targetRef=\"b\"/>");
        this.withEngine(context -> {
            BpmnNodeTaskTest.execute(context, instance, "g");
            Assertions.assertEquals(List.of("b"), this.triggeredNodes(context));
        });
    }

    @Test
    void exclusiveGatewayTakesSingleFlow() {
        final ProcessInstance instance = this.instance("<exclusiveGateway id=\"g\"/><task id=\"a\"/>"
                + "<sequenceFlow id=\"f1\" sourceRef=\"g\" targetRef=\"a\"/>");
        this.withEngine(context -> {
            BpmnNodeTaskTest.execute(context, instance, "g");
            Assertions.assertEquals(List.of("a"), this.triggeredNodes(context));
        });
    }

    @Test
    void ambiguousExclusiveGatewayFails() {
        final ProcessInstance instance = this.instance("<exclusiveGateway id=\"g\"/><task id=\"a\"/><task id=\"b\"/>"
                + "<sequenceFlow id=\"f1\" sourceRef=\"g\" targetRef=\"a\"/>"
                + "<sequenceFlow id=\"f2\" sourceRef=\"g\" targetRef=\"b\"/>");
        this.withEngine(context -> {
            BpmnNodeTaskTest.assertFails(context, instance, "g");
            Assertions.assertTrue(this.tasks.persisted().isEmpty());
        });
    }

    @Test
    void parallelGatewayForksToAllFlows() {
        final ProcessInstance instance = this.instance("<parallelGateway id=\"g\"/><task id=\"a\"/><task id=\"b\"/>"
                + "<sequenceFlow id=\"f1\" sourceRef=\"g\" targetRef=\"a\"/>"
                + "<sequenceFlow id=\"f2\" sourceRef=\"g\" targetRef=\"b\"/>");
        this.withEngine(context -> {
            BpmnNodeTaskTest.execute(context, instance, "g");
            Assertions.assertEquals(List.of("a", "b"), this.triggeredNodes(context));
        });
    }

    @Test
    void conditionalFlowFails() {
        final ProcessInstance instance = this.instance("<task id=\"t\"/><task id=\"a\"/>"
                + "<sequenceFlow id=\"f\" sourceRef=\"t\" targetRef=\"a\"><conditionExpression>${ok}</conditionExpression>"
                + "</sequenceFlow>");
        this.withEngine(context -> {
            BpmnNodeTaskTest.assertFails(context, instance, "t");
            Assertions.assertTrue(this.tasks.persisted().isEmpty());
        });
    }

    @Test
    void unsupportedNodeTypeFails() {
        final ProcessInstance instance = this.instance("<userTask id=\"u\"/>");
        this.withEngine(context -> BpmnNodeTaskTest.assertFails(context, instance, "u"));
    }

    @Test
    void loopActivityFails() {
        final ProcessInstance instance = this.instance("<task id=\"t\"><standardLoopCharacteristics/></task>");
        this.withEngine(context -> BpmnNodeTaskTest.assertFails(context, instance, "t"));
    }

    @Test
    void unknownNodeOrInstanceFails() {
        final ProcessInstance instance = this.instance("<task id=\"t\"/><sequenceFlow id=\"f\" sourceRef=\"t\" targetRef=\"t\"/>");
        this.withEngine(context -> {
            BpmnNodeTaskTest.assertFails(context, instance, "missing");
            BpmnNodeTaskTest.assertFails(context, instance, "f");
            Assertions.assertThrows(TaskException.class, () -> context.getBean(BpmnNodeTask.BEAN_NAME, BpmnNodeTask.class)
                    .execute(new NodeTaskPayload(UUID.randomUUID(), "t")));
        });
    }
}
