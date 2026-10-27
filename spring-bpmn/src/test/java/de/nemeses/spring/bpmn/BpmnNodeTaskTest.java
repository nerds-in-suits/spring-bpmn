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
    private static final String XSI_NS = "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" ";

    private final InMemoryProcessInstanceRepository instances = new InMemoryProcessInstanceRepository();
    private final RecordingTaskPersistence tasks = new RecordingTaskPersistence();
    private final List<BpmnNodeContext> calls = new ArrayList<>();

    private ProcessInstance instance(final String processContent) {
        return this.instance("", processContent);
    }

    private ProcessInstance instance(final String definitionsAttributes, final String processContent) {
        final String bpmnXml = "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" " + SPRING_NS
                + XSI_NS + definitionsAttributes + " id=\"d\" targetNamespace=\"urn:t\"><process id=\"p\">"
                + processContent + "</process></definitions>";
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
                + BpmnNodeTaskTest.flow("f1", "g", "a", "${amount > 5}")
                + "<sequenceFlow id=\"f2\" sourceRef=\"g\" targetRef=\"b\"/>");
        this.withEngine(context -> {
            BpmnNodeTaskTest.execute(context, instance, "g");
            Assertions.assertEquals(List.of("b"), this.triggeredNodes(context));
        });
    }

    @Test
    void exclusiveGatewayTakesFirstTrueFlowInDocumentOrder() {
        final ProcessInstance instance = this.instance("<exclusiveGateway id=\"g\" default=\"f1\"/>"
                + "<task id=\"a\"/><task id=\"b\"/><task id=\"c\"/><task id=\"d\"/>"
                + "<sequenceFlow id=\"f1\" sourceRef=\"g\" targetRef=\"a\"/>"
                + BpmnNodeTaskTest.flow("f2", "g", "b", "amount > 5")
                + BpmnNodeTaskTest.flow("f3", "g", "c", "${amount &gt;= 3}")
                + BpmnNodeTaskTest.flow("f4", "g", "d", "#amount == 3"));
        this.withEngine(context -> {
            BpmnNodeTaskTest.execute(context, instance, "g");
            Assertions.assertEquals(List.of("c"), this.triggeredNodes(context));
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
    void exclusiveGatewayWithoutTrueConditionOrDefaultFails() {
        final ProcessInstance instance = this.instance("<exclusiveGateway id=\"g\"/><task id=\"a\"/><task id=\"b\"/>"
                + BpmnNodeTaskTest.flow("f1", "g", "a", "amount > 5")
                + BpmnNodeTaskTest.flow("f2", "g", "b", "amount &lt; 0"));
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
    void activityTakesTrueAndUnconditionalFlows() {
        final ProcessInstance instance = this.instance("<task id=\"t\" default=\"f4\"/>"
                + "<task id=\"a\"/><task id=\"b\"/><task id=\"c\"/><task id=\"d\"/><task id=\"e\"/>"
                + BpmnNodeTaskTest.flow("f1", "t", "a", "amount == 3")
                + BpmnNodeTaskTest.flow("f2", "t", "b", "amount != 3")
                + "<sequenceFlow id=\"f3\" sourceRef=\"t\" targetRef=\"c\"/>"
                + "<sequenceFlow id=\"f4\" sourceRef=\"t\" targetRef=\"d\"/>"
                + BpmnNodeTaskTest.flow("f5", "t", "e", "true"));
        this.withEngine(context -> {
            BpmnNodeTaskTest.execute(context, instance, "t");
            Assertions.assertEquals(List.of("a", "c", "e"), this.triggeredNodes(context));
        });
    }

    @Test
    void activityTakesDefaultFlowIfNoConditionIsTrue() {
        final ProcessInstance instance = this.instance("<serviceTask id=\"st\" spring:bean=\"recording\" default=\"f3\"/>"
                + "<task id=\"a\"/><task id=\"b\"/><task id=\"c\"/>"
                + BpmnNodeTaskTest.flow("f1", "st", "a", "amount > 5")
                + "<sequenceFlow id=\"f2\" sourceRef=\"st\" targetRef=\"b\"/>"
                + "<sequenceFlow id=\"f3\" sourceRef=\"st\" targetRef=\"c\"/>");
        this.withEngine(context -> {
            BpmnNodeTaskTest.execute(context, instance, "st");
            Assertions.assertEquals(List.of("b", "c"), this.triggeredNodes(context));
        });
    }

    @Test
    void formalExpressionWithSpelLanguageIsEvaluated() {
        final ProcessInstance instance = this.instance("expressionLanguage=\"urn:other\"",
                "<task id=\"t\"/><task id=\"a\"/><sequenceFlow id=\"f\" sourceRef=\"t\" targetRef=\"a\">"
                        + "<conditionExpression xsi:type=\"tFormalExpression\" language=\""
                        + ConditionEvaluator.SPEL_LANGUAGE + "\">${amount == 3}</conditionExpression></sequenceFlow>");
        this.withEngine(context -> {
            BpmnNodeTaskTest.execute(context, instance, "t");
            Assertions.assertEquals(List.of("a"), this.triggeredNodes(context));
        });
    }

    @Test
    void definitionsWithSpelLanguageAreEvaluated() {
        final ProcessInstance instance = this.instance("expressionLanguage=\"" + ConditionEvaluator.SPEL_LANGUAGE + "\"",
                "<task id=\"t\"/><task id=\"a\"/>" + BpmnNodeTaskTest.flow("f", "t", "a", "amount == 3"));
        this.withEngine(context -> {
            BpmnNodeTaskTest.execute(context, instance, "t");
            Assertions.assertEquals(List.of("a"), this.triggeredNodes(context));
        });
    }

    @Test
    void unsupportedExpressionLanguageFails() {
        final String process = "<task id=\"t\"/><task id=\"a\"/>"
                + "<sequenceFlow id=\"f\" sourceRef=\"t\" targetRef=\"a\">"
                + "<conditionExpression xsi:type=\"tFormalExpression\" language=\"urn:other\">true</conditionExpression>"
                + "</sequenceFlow>";
        final ProcessInstance formal = this.instance(process);
        final ProcessInstance definitions = this.instance("expressionLanguage=\"urn:other\"",
                "<task id=\"t\"/><task id=\"a\"/>" + BpmnNodeTaskTest.flow("f", "t", "a", "true"));
        this.withEngine(context -> {
            BpmnNodeTaskTest.assertFails(context, formal, "t");
            BpmnNodeTaskTest.assertFails(context, definitions, "t");
            Assertions.assertTrue(this.tasks.persisted().isEmpty());
        });
    }

    @Test
    void invalidConditionFails() {
        final ProcessInstance instance = this.instance("<task id=\"unknownVariable\"/><task id=\"notBoolean\"/>"
                + "<task id=\"syntax\"/><task id=\"empty\"/><task id=\"typeReference\"/><task id=\"a\"/>"
                + BpmnNodeTaskTest.flow("f1", "unknownVariable", "a", "ok")
                + BpmnNodeTaskTest.flow("f2", "notBoolean", "a", "amount")
                + BpmnNodeTaskTest.flow("f3", "syntax", "a", "amount &gt;")
                + BpmnNodeTaskTest.flow("f4", "empty", "a", " ")
                + BpmnNodeTaskTest.flow("f5", "typeReference", "a", "T(java.lang.Boolean).TRUE"));
        this.withEngine(context -> {
            BpmnNodeTaskTest.assertFails(context, instance, "unknownVariable");
            BpmnNodeTaskTest.assertFails(context, instance, "notBoolean");
            BpmnNodeTaskTest.assertFails(context, instance, "syntax");
            BpmnNodeTaskTest.assertFails(context, instance, "empty");
            BpmnNodeTaskTest.assertFails(context, instance, "typeReference");
            Assertions.assertTrue(this.tasks.persisted().isEmpty());
        });
    }

    @Test
    void conditionalFlowOutOfStartEventFails() {
        final ProcessInstance instance = this.instance("<startEvent id=\"s\"/><task id=\"a\"/>"
                + BpmnNodeTaskTest.flow("f", "s", "a", "true"));
        this.withEngine(context -> {
            BpmnNodeTaskTest.assertFails(context, instance, "s");
            Assertions.assertTrue(this.tasks.persisted().isEmpty());
        });
    }

    private static String flow(final String id, final String source, final String target, final String condition) {
        return "<sequenceFlow id=\"" + id + "\" sourceRef=\"" + source + "\" targetRef=\"" + target + "\">"
                + "<conditionExpression>" + condition + "</conditionExpression></sequenceFlow>";
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
