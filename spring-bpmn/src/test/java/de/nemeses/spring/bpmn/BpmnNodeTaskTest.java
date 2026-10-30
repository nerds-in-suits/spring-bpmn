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

import de.nemeses.spring.bpmn.repository.NoSuchProcessInstanceException;
import de.nemeses.spring.bpmn.repository.NoSuchProcessStepException;
import de.nemeses.spring.bpmn.repository.ProcessDefinition;
import de.nemeses.spring.bpmn.repository.ProcessDefinitionService;
import de.nemeses.spring.bpmn.repository.ProcessInstance;
import de.nemeses.spring.bpmn.repository.ProcessInstanceRepository;
import de.nemeses.spring.bpmn.repository.ProcessStep;
import de.nemeses.spring.bpmn.repository.ProcessStepRepository;
import de.nemeses.spring.tasks.TaskConfiguration;
import de.nemeses.spring.tasks.TaskException;
import de.nemeses.spring.tasks.TaskPersistence;

/**
 * Executes single steps through the {@link BpmnNodeTask} with in-memory repositories and recorded tasks.
 */
class BpmnNodeTaskTest {

    private static final String SPRING_NS = "xmlns:spring=\"" + BpmnNodeExecutor.NAMESPACE + "\" ";
    private static final String XSI_NS = "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" ";

    private final InMemoryProcessInstanceRepository instances = new InMemoryProcessInstanceRepository();
    private final InMemoryProcessStepRepository steps = new InMemoryProcessStepRepository(this.instances);
    private final RecordingTaskPersistence tasks = new RecordingTaskPersistence();
    private final List<BpmnNodeContext> calls = new ArrayList<>();

    private ProcessInstance instance(final String processContent) {
        return this.instance("", processContent);
    }

    private ProcessInstance instance(final String definitionsAttributes, final String processContent) {
        final String bpmnXml = "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" " + SPRING_NS
                + XSI_NS + definitionsAttributes + " id=\"definitions\" targetNamespace=\"urn:t\"><process id=\"p\">"
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
                .withBean(ProcessStepRepository.class, () -> this.steps)
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
                .withBean("counting", BpmnNodeImplementation.class, () -> context -> {
                    context.setVariable("count", ((Integer) context.variables().getOrDefault("count", 0)) + 1);
                    this.calls.add(context);
                })
                .withBean("countingThenFailing", BpmnNodeImplementation.class, () -> context -> {
                    context.setVariable("count", ((Integer) context.variables().getOrDefault("count", 0)) + 1);
                    if (context.loopCounter() == 1) {
                        throw new ProcessEngineException("failed");
                    }
                })
                .run(test);
    }

    private static void execute(final AssertableApplicationContext context, final ProcessStep step)
            throws TaskException {
        context.getBean(BpmnNodeTask.BEAN_NAME, BpmnNodeTask.class).execute(step.id());
    }

    /**
     * Creates a step (without incoming flow) for the node and executes it.
     */
    private ProcessStep execute(final AssertableApplicationContext context, final ProcessInstance instance,
            final String nodeId) throws TaskException, NoSuchProcessInstanceException {
        final ProcessStep step = this.steps.create(instance.id(), nodeId, null);
        BpmnNodeTaskTest.execute(context, step);
        return step;
    }

    private void assertFails(final AssertableApplicationContext context, final ProcessInstance instance,
            final String nodeId) throws NoSuchProcessInstanceException {
        final ProcessStep step = this.steps.create(instance.id(), nodeId, null);
        final TaskException e = Assertions.assertThrows(TaskException.class,
                () -> BpmnNodeTaskTest.execute(context, step));
        Assertions.assertInstanceOf(ProcessEngineException.class, e.getCause());
    }

    /**
     * @return the steps of all triggered tasks, in trigger order
     */
    private List<ProcessStep> triggered(final AssertableApplicationContext context) {
        final BpmnNodeTask nodeTask = context.getBean(BpmnNodeTask.BEAN_NAME, BpmnNodeTask.class);
        final List<ProcessStep> result = new ArrayList<>();
        for (final RecordingTaskPersistence.Recorded task : this.tasks.persisted()) {
            Assertions.assertEquals(BpmnNodeTask.BEAN_NAME, task.beanName());
            try {
                result.add(this.steps.retrieve(nodeTask.deserializePayload(task.payload())));
            } catch (final NoSuchProcessStepException e) {
                throw new AssertionError(e);
            }
        }
        return result;
    }

    private List<String> triggeredNodes(final AssertableApplicationContext context) {
        return this.triggered(context).stream().map(ProcessStep::nodeId).toList();
    }

    private ProcessStep triggered(final AssertableApplicationContext context, final String nodeId) {
        return this.triggered(context).stream().filter(step -> step.nodeId().equals(nodeId)).findFirst().orElseThrow();
    }

    private Date endDate(final ProcessInstance instance) {
        return this.instances.created().stream().filter(i -> i.id().equals(instance.id())).findFirst().orElseThrow()
                .endDate();
    }

    private ProcessStep stored(final ProcessStep step) throws NoSuchProcessStepException {
        return this.steps.retrieve(step.id());
    }

    @Test
    void startTaskAndEndRunThroughProcess() {
        final ProcessInstance instance = this.instance("<startEvent id=\"s\"/><task id=\"t\"/><endEvent id=\"e\"/>"
                + "<sequenceFlow id=\"f1\" sourceRef=\"s\" targetRef=\"t\"/>"
                + "<sequenceFlow id=\"f2\" sourceRef=\"t\" targetRef=\"e\"/>");
        this.withEngine(context -> {
            final ProcessStep start = this.execute(context, instance, "s");
            final ProcessStep task = this.triggered(context, "t");
            Assertions.assertEquals(instance.id(), task.processInstanceId());
            Assertions.assertEquals("f1", task.incomingFlowId());
            Assertions.assertNull(task.endDate());
            Assertions.assertNotNull(this.stored(start).endDate());
            Assertions.assertNull(this.endDate(instance));

            this.execute(context, task);
            Assertions.assertEquals(List.of("t", "e"), this.triggeredNodes(context));

            this.execute(context, this.triggered(context, "e"));
            Assertions.assertEquals(List.of("t", "e"), this.triggeredNodes(context));
            Assertions.assertNotNull(this.endDate(instance));

            final List<ProcessStep> trail = this.steps.steps(instance.id());
            Assertions.assertEquals(List.of("s", "t", "e"), trail.stream().map(ProcessStep::nodeId).toList());
            Assertions.assertTrue(trail.stream().allMatch(step -> step.endDate() != null));
        });
    }

    @Test
    void completedStepIsSkipped() {
        final ProcessInstance instance = this.instance("<task id=\"t\"/><task id=\"a\"/>"
                + "<sequenceFlow id=\"f\" sourceRef=\"t\" targetRef=\"a\"/>");
        this.withEngine(context -> {
            final ProcessStep step = this.execute(context, instance, "t");
            final Date completed = this.stored(step).endDate();

            BpmnNodeTaskTest.execute(context, step);
            Assertions.assertEquals(List.of("a"), this.triggeredNodes(context));
            Assertions.assertSame(completed, this.stored(step).endDate());
        });
    }

    @Test
    void endEventEndsInstanceOnlyWithoutPendingSteps() {
        final ProcessInstance instance = this.instance("<startEvent id=\"s1\"/><startEvent id=\"s2\"/>"
                + "<endEvent id=\"e1\"/><endEvent id=\"e2\"/>"
                + "<sequenceFlow id=\"f1\" sourceRef=\"s1\" targetRef=\"e1\"/>"
                + "<sequenceFlow id=\"f2\" sourceRef=\"s2\" targetRef=\"e2\"/>");
        this.withEngine(context -> {
            final ProcessStep start1 = this.steps.create(instance.id(), "s1", null);
            final ProcessStep start2 = this.steps.create(instance.id(), "s2", null);
            this.execute(context, start1);
            this.execute(context, this.triggered(context, "e1"));
            Assertions.assertNull(this.endDate(instance));

            this.execute(context, start2);
            Assertions.assertNull(this.endDate(instance));
            this.execute(context, this.triggered(context, "e2"));
            Assertions.assertNotNull(this.endDate(instance));
        });
    }

    @Test
    void parallelGatewayJoinsBranchesOnce() {
        final ProcessInstance instance = this.instance("<parallelGateway id=\"fork\"/><task id=\"a\"/><task id=\"b\"/>"
                + "<parallelGateway id=\"join\"/><endEvent id=\"e\"/>"
                + "<sequenceFlow id=\"fa\" sourceRef=\"fork\" targetRef=\"a\"/>"
                + "<sequenceFlow id=\"fb\" sourceRef=\"fork\" targetRef=\"b\"/>"
                + "<sequenceFlow id=\"ja\" sourceRef=\"a\" targetRef=\"join\"/>"
                + "<sequenceFlow id=\"jb\" sourceRef=\"b\" targetRef=\"join\"/>"
                + "<sequenceFlow id=\"fe\" sourceRef=\"join\" targetRef=\"e\"/>");
        this.withEngine(context -> {
            this.execute(context, instance, "fork");
            Assertions.assertEquals(List.of("a", "b"), this.triggeredNodes(context));

            this.execute(context, this.triggered(context, "a"));
            final ProcessStep joinA = this.triggered(context).get(2);
            Assertions.assertEquals("ja", joinA.incomingFlowId());
            this.execute(context, joinA);
            // parked at the join: still pending, nothing taken
            Assertions.assertNull(this.stored(joinA).endDate());
            Assertions.assertEquals(List.of("a", "b", "join"), this.triggeredNodes(context));

            this.execute(context, this.triggered(context, "b"));
            final ProcessStep joinB = this.triggered(context).get(3);
            Assertions.assertEquals("jb", joinB.incomingFlowId());
            this.execute(context, joinB);
            Assertions.assertNotNull(this.stored(joinA).endDate());
            Assertions.assertNotNull(this.stored(joinB).endDate());
            Assertions.assertEquals(List.of("a", "b", "join", "join", "e"), this.triggeredNodes(context));

            this.execute(context, this.triggered(context, "e"));
            Assertions.assertNotNull(this.endDate(instance));
        });
    }

    @Test
    void parallelGatewayJoinContinuesOnFirstCompleteSet() {
        final ProcessInstance instance = this.instance("<parallelGateway id=\"join\"/><task id=\"t\"/>"
                + "<task id=\"a\"/><task id=\"b\"/>"
                + "<sequenceFlow id=\"ja\" sourceRef=\"a\" targetRef=\"join\"/>"
                + "<sequenceFlow id=\"jb\" sourceRef=\"b\" targetRef=\"join\"/>"
                + "<sequenceFlow id=\"ft\" sourceRef=\"join\" targetRef=\"t\"/>");
        this.withEngine(context -> {
            final ProcessStep a1 = this.steps.create(instance.id(), "join", "ja");
            final ProcessStep a2 = this.steps.create(instance.id(), "join", "ja");
            final ProcessStep b1 = this.steps.create(instance.id(), "join", "jb");

            // the newer step on flow ja completes the join with the oldest one of each flow
            this.execute(context, a2);
            Assertions.assertEquals(List.of("t"), this.triggeredNodes(context));
            Assertions.assertNotNull(this.stored(a1).endDate());
            Assertions.assertNotNull(this.stored(b1).endDate());
            Assertions.assertNull(this.stored(a2).endDate());

            // the steps completed by the join are skipped, a2 waits for the next step on jb
            this.execute(context, a1);
            this.execute(context, b1);
            this.execute(context, a2);
            Assertions.assertEquals(List.of("t"), this.triggeredNodes(context));
            Assertions.assertNull(this.stored(a2).endDate());
        });
    }

    @Test
    void stepOfEndedInstanceIsSkipped() {
        final ProcessInstance instance = this.instance("<task id=\"t\"/><endEvent id=\"e\"/><endEvent id=\"e2\"/>"
                + "<sequenceFlow id=\"f\" sourceRef=\"t\" targetRef=\"e2\"/>");
        this.withEngine(context -> {
            this.execute(context, instance, "e");
            final Date endDate = this.endDate(instance);

            final ProcessStep task = this.execute(context, instance, "t");
            this.execute(context, instance, "e2");
            Assertions.assertTrue(this.tasks.persisted().isEmpty());
            Assertions.assertSame(endDate, this.endDate(instance));
            Assertions.assertNull(this.stored(task).endDate());
        });
    }

    @Test
    void endEventWithEventDefinitionFails() {
        final ProcessInstance instance = this.instance("<endEvent id=\"e\"><terminateEventDefinition/></endEvent>");
        this.withEngine(context -> {
            this.assertFails(context, instance, "e");
            Assertions.assertNull(this.endDate(instance));
        });
    }

    @Test
    void serviceTaskCallsImplementationWithVariables() {
        final ProcessInstance instance = this.instance("<serviceTask id=\"st\" spring:bean=\"recording\"/>"
                + "<endEvent id=\"e\"/><sequenceFlow id=\"f\" sourceRef=\"st\" targetRef=\"e\"/>");
        this.withEngine(context -> {
            this.execute(context, instance, "st");

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
            this.execute(context, instance, "st");

            Assertions.assertEquals(Map.of("result", "ok"), this.calls.get(0).variables());
            Assertions.assertEquals(Map.of("result", "ok"), this.instances.retrieve(instance.id()).variables());
            Assertions.assertEquals(List.of("e"), this.triggeredNodes(context));
        });
    }

    @Test
    void conditionsOutOfServiceTaskSeeChangedVariables() {
        final ProcessInstance instance = this.instance("<serviceTask id=\"st\" spring:bean=\"writing\" default=\"f3\"/>"
                + "<task id=\"a\"/><task id=\"b\"/><task id=\"c\"/>"
                + BpmnNodeTaskTest.flow("f1", "st", "a", "result == 'ok'")
                + BpmnNodeTaskTest.flow("f2", "st", "b", "#amount == null")
                + "<sequenceFlow id=\"f3\" sourceRef=\"st\" targetRef=\"c\"/>");
        this.withEngine(context -> {
            this.execute(context, instance, "st");
            Assertions.assertEquals(List.of("a", "b"), this.triggeredNodes(context));
        });
    }

    @Test
    void serviceTaskRejectsNullVariable() {
        final ProcessInstance instance = this.instance("<serviceTask id=\"st\" spring:bean=\"recording\"/>");
        this.withEngine(context -> {
            this.execute(context, instance, "st");

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
            this.assertFails(context, instance, "st");

            Assertions.assertEquals(Map.of("amount", 3), this.instances.retrieve(instance.id()).variables());
            Assertions.assertTrue(this.tasks.persisted().isEmpty());
        });
    }

    @Test
    void failingServiceTaskTakesNoFlow() {
        final ProcessInstance instance = this.instance("<serviceTask id=\"st\" spring:bean=\"failing\"/>"
                + "<endEvent id=\"e\"/><sequenceFlow id=\"f\" sourceRef=\"st\" targetRef=\"e\"/>");
        this.withEngine(context -> {
            final ProcessStep step = this.steps.create(instance.id(), "st", null);
            final TaskException e = Assertions.assertThrows(TaskException.class,
                    () -> BpmnNodeTaskTest.execute(context, step));
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
            this.assertFails(context, instance, "none");
            this.assertFails(context, instance, "unknown");
            this.assertFails(context, instance, "wrongType");
        });
    }

    @Test
    void scriptTaskStoresAssignedVariablesForConditions() {
        final ProcessInstance instance = this.instance("<scriptTask id=\"st\" scriptFormat=\""
                + ConditionEvaluator.SPEL_LANGUAGE + "\" default=\"f2\"><script>amount = amount * 2;"
                + " result = (amount &gt; 5 ? 'big' : 'small')</script></scriptTask>"
                + "<task id=\"a\"/><task id=\"b\"/>"
                + BpmnNodeTaskTest.flow("f1", "st", "a", "result == 'big'")
                + "<sequenceFlow id=\"f2\" sourceRef=\"st\" targetRef=\"b\"/>");
        this.withEngine(context -> {
            this.execute(context, instance, "st");

            Assertions.assertEquals(Map.of("amount", 6, "result", "big"),
                    this.instances.retrieve(instance.id()).variables());
            Assertions.assertEquals(List.of("a"), this.triggeredNodes(context));
        });
    }

    @Test
    void failingScriptTaskDiscardsVariablesAndTakesNoFlow() {
        final ProcessInstance instance = this.instance("<scriptTask id=\"st\"><script>result = 'ok'; x = missing"
                + "</script></scriptTask><scriptTask id=\"groovy\" scriptFormat=\"groovy\"><script>x = 1</script>"
                + "</scriptTask><endEvent id=\"e\"/><sequenceFlow id=\"f\" sourceRef=\"st\" targetRef=\"e\"/>"
                + "<sequenceFlow id=\"f2\" sourceRef=\"groovy\" targetRef=\"e\"/>");
        this.withEngine(context -> {
            this.assertFails(context, instance, "st");
            this.assertFails(context, instance, "groovy");

            Assertions.assertEquals(Map.of("amount", 3), this.instances.retrieve(instance.id()).variables());
            Assertions.assertTrue(this.tasks.persisted().isEmpty());
        });
    }

    @Test
    void exclusiveGatewayTakesDefaultFlow() {
        final ProcessInstance instance = this.instance("<exclusiveGateway id=\"g\" default=\"f2\"/>"
                + "<task id=\"a\"/><task id=\"b\"/>"
                + BpmnNodeTaskTest.flow("f1", "g", "a", "${amount > 5}")
                + "<sequenceFlow id=\"f2\" sourceRef=\"g\" targetRef=\"b\"/>");
        this.withEngine(context -> {
            this.execute(context, instance, "g");
            Assertions.assertEquals(List.of("b"), this.triggeredNodes(context));
            Assertions.assertEquals("f2", this.triggered(context, "b").incomingFlowId());
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
            this.execute(context, instance, "g");
            Assertions.assertEquals(List.of("c"), this.triggeredNodes(context));
        });
    }

    @Test
    void exclusiveGatewayTakesSingleFlow() {
        final ProcessInstance instance = this.instance("<exclusiveGateway id=\"g\"/><task id=\"a\"/>"
                + "<sequenceFlow id=\"f1\" sourceRef=\"g\" targetRef=\"a\"/>");
        this.withEngine(context -> {
            this.execute(context, instance, "g");
            Assertions.assertEquals(List.of("a"), this.triggeredNodes(context));
        });
    }

    @Test
    void exclusiveGatewayWithoutTrueConditionOrDefaultFails() {
        final ProcessInstance instance = this.instance("<exclusiveGateway id=\"g\"/><task id=\"a\"/><task id=\"b\"/>"
                + BpmnNodeTaskTest.flow("f1", "g", "a", "amount > 5")
                + BpmnNodeTaskTest.flow("f2", "g", "b", "amount &lt; 0"));
        this.withEngine(context -> {
            this.assertFails(context, instance, "g");
            Assertions.assertTrue(this.tasks.persisted().isEmpty());
        });
    }

    @Test
    void parallelGatewayForksToAllFlows() {
        final ProcessInstance instance = this.instance("<parallelGateway id=\"g\"/><task id=\"a\"/><task id=\"b\"/>"
                + "<sequenceFlow id=\"f1\" sourceRef=\"g\" targetRef=\"a\"/>"
                + "<sequenceFlow id=\"f2\" sourceRef=\"g\" targetRef=\"b\"/>");
        this.withEngine(context -> {
            this.execute(context, instance, "g");
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
            this.execute(context, instance, "t");
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
            this.execute(context, instance, "st");
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
            this.execute(context, instance, "t");
            Assertions.assertEquals(List.of("a"), this.triggeredNodes(context));
        });
    }

    @Test
    void definitionsWithSpelLanguageAreEvaluated() {
        final ProcessInstance instance = this.instance("expressionLanguage=\"" + ConditionEvaluator.SPEL_LANGUAGE + "\"",
                "<task id=\"t\"/><task id=\"a\"/>" + BpmnNodeTaskTest.flow("f", "t", "a", "amount == 3"));
        this.withEngine(context -> {
            this.execute(context, instance, "t");
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
            this.assertFails(context, formal, "t");
            this.assertFails(context, definitions, "t");
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
            this.assertFails(context, instance, "unknownVariable");
            this.assertFails(context, instance, "notBoolean");
            this.assertFails(context, instance, "syntax");
            this.assertFails(context, instance, "empty");
            this.assertFails(context, instance, "typeReference");
            Assertions.assertTrue(this.tasks.persisted().isEmpty());
        });
    }

    @Test
    void conditionalFlowOutOfStartEventFails() {
        final ProcessInstance instance = this.instance("<startEvent id=\"s\"/><task id=\"a\"/>"
                + BpmnNodeTaskTest.flow("f", "s", "a", "true"));
        this.withEngine(context -> {
            this.assertFails(context, instance, "s");
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
        this.withEngine(context -> this.assertFails(context, instance, "u"));
    }

    private ProcessInstance loopingServiceTask(final String bean, final String loop) {
        return this.instance("<serviceTask id=\"st\" spring:bean=\"" + bean + "\">" + loop + "</serviceTask>"
                + "<endEvent id=\"e\"/><sequenceFlow id=\"f\" sourceRef=\"st\" targetRef=\"e\"/>");
    }

    private List<Integer> loopCounters() {
        return this.calls.stream().map(BpmnNodeContext::loopCounter).toList();
    }

    @Test
    void standardLoopRepeatsWhileConditionIsTrue() {
        final ProcessInstance instance = this.loopingServiceTask("counting",
                "<standardLoopCharacteristics><loopCondition>count &lt; 3</loopCondition>"
                        + "</standardLoopCharacteristics>");
        this.withEngine(context -> {
            final ProcessStep step = this.execute(context, instance, "st");

            Assertions.assertEquals(List.of(0, 1, 2), this.loopCounters());
            // each iteration sees the changes of the previous ones
            Assertions.assertEquals(Map.of("amount", 3, "count", 2), this.calls.get(1).variables());
            Assertions.assertEquals(Map.of("amount", 3, "count", 3), this.instances.retrieve(instance.id()).variables());
            Assertions.assertEquals(List.of("e"), this.triggeredNodes(context));
            Assertions.assertNotNull(this.stored(step).endDate());
        });
    }

    @Test
    void standardLoopWithoutTestBeforeRunsOnceEvenIfConditionIsFalse() {
        final ProcessInstance instance = this.loopingServiceTask("counting",
                "<standardLoopCharacteristics><loopCondition>false</loopCondition></standardLoopCharacteristics>");
        this.withEngine(context -> {
            this.execute(context, instance, "st");

            Assertions.assertEquals(List.of(0), this.loopCounters());
            Assertions.assertEquals(Map.of("amount", 3, "count", 1), this.instances.retrieve(instance.id()).variables());
        });
    }

    @Test
    void standardLoopWithTestBeforeMayRunNever() {
        final ProcessInstance instance = this.loopingServiceTask("counting",
                "<standardLoopCharacteristics testBefore=\"true\"><loopCondition>amount &gt; 5</loopCondition>"
                        + "</standardLoopCharacteristics>");
        this.withEngine(context -> {
            this.execute(context, instance, "st");

            Assertions.assertTrue(this.calls.isEmpty());
            Assertions.assertEquals(Map.of("amount", 3), this.instances.retrieve(instance.id()).variables());
            Assertions.assertEquals(List.of("e"), this.triggeredNodes(context));
        });
    }

    @Test
    void standardLoopConditionSeesLoopCounter() {
        final ProcessInstance instance = this.loopingServiceTask("counting",
                "<standardLoopCharacteristics testBefore=\"true\"><loopCondition>${loopCounter &lt; 2}</loopCondition>"
                        + "</standardLoopCharacteristics>");
        this.withEngine(context -> {
            this.execute(context, instance, "st");

            Assertions.assertEquals(List.of(0, 1), this.loopCounters());
            Assertions.assertFalse(this.instances.retrieve(instance.id()).variables().containsKey("loopCounter"));
        });
    }

    @Test
    void standardLoopStopsAtLoopMaximum() {
        final ProcessInstance instance = this.loopingServiceTask("counting",
                "<standardLoopCharacteristics loopMaximum=\"2\"><loopCondition>true</loopCondition>"
                        + "</standardLoopCharacteristics>");
        this.withEngine(context -> {
            this.execute(context, instance, "st");
            Assertions.assertEquals(List.of(0, 1), this.loopCounters());
        });
    }

    @Test
    void standardLoopWithoutConditionRunsLoopMaximumTimes() {
        final ProcessInstance instance = this.loopingServiceTask("counting",
                "<standardLoopCharacteristics loopMaximum=\"3\"/>");
        this.withEngine(context -> {
            this.execute(context, instance, "st");
            Assertions.assertEquals(List.of(0, 1, 2), this.loopCounters());
        });
    }

    @Test
    void standardLoopWithLoopMaximumZeroRunsNever() {
        final ProcessInstance instance = this.loopingServiceTask("counting",
                "<standardLoopCharacteristics loopMaximum=\"0\"/>");
        this.withEngine(context -> {
            this.execute(context, instance, "st");
            Assertions.assertTrue(this.calls.isEmpty());
            Assertions.assertEquals(List.of("e"), this.triggeredNodes(context));
        });
    }

    @Test
    void plainTaskLoopTakesOutgoingFlowsOnce() {
        final ProcessInstance instance = this.instance("<task id=\"t\"><standardLoopCharacteristics loopMaximum=\"5\">"
                + "<loopCondition>loopCounter &lt; 3</loopCondition></standardLoopCharacteristics></task>"
                + "<endEvent id=\"e\"/><sequenceFlow id=\"f\" sourceRef=\"t\" targetRef=\"e\"/>");
        this.withEngine(context -> {
            this.execute(context, instance, "t");
            Assertions.assertEquals(List.of("e"), this.triggeredNodes(context));
        });
    }

    @Test
    void scriptTaskLoopSeesChangesOfPreviousIterations() {
        final ProcessInstance instance = this.instance("<scriptTask id=\"st\"><standardLoopCharacteristics>"
                + "<loopCondition>amount &lt; 20</loopCondition></standardLoopCharacteristics>"
                + "<script>amount = amount * 2</script></scriptTask>"
                + "<endEvent id=\"e\"/><sequenceFlow id=\"f\" sourceRef=\"st\" targetRef=\"e\"/>");
        this.withEngine(context -> {
            this.execute(context, instance, "st");

            Assertions.assertEquals(Map.of("amount", 24), this.instances.retrieve(instance.id()).variables());
            Assertions.assertEquals(List.of("e"), this.triggeredNodes(context));
        });
    }

    @Test
    void failingLoopIterationDiscardsAllIterations() {
        final ProcessInstance instance = this.loopingServiceTask("countingThenFailing",
                "<standardLoopCharacteristics loopMaximum=\"3\"/>");
        this.withEngine(context -> {
            this.assertFails(context, instance, "st");

            Assertions.assertEquals(Map.of("amount", 3), this.instances.retrieve(instance.id()).variables());
            Assertions.assertTrue(this.tasks.persisted().isEmpty());
        });
    }

    @Test
    void invalidStandardLoopFails() {
        final ProcessInstance instance = this.instance("<task id=\"endless\"><standardLoopCharacteristics/></task>"
                + "<task id=\"negative\"><standardLoopCharacteristics loopMaximum=\"-1\"/></task>"
                + "<task id=\"nonBoolean\"><standardLoopCharacteristics><loopCondition>amount</loopCondition>"
                + "</standardLoopCharacteristics></task>");
        this.withEngine(context -> {
            this.assertFails(context, instance, "endless");
            this.assertFails(context, instance, "negative");
            this.assertFails(context, instance, "nonBoolean");
        });
    }

    @Test
    void multiInstanceActivityFails() {
        final ProcessInstance instance = this.instance("<task id=\"t\"><multiInstanceLoopCharacteristics/></task>"
                + "<serviceTask id=\"st\" spring:bean=\"recording\"><multiInstanceLoopCharacteristics "
                + "isSequential=\"true\"/></serviceTask>");
        this.withEngine(context -> {
            this.assertFails(context, instance, "t");
            this.assertFails(context, instance, "st");
            Assertions.assertTrue(this.calls.isEmpty());
        });
    }

    @Test
    void unknownNodeOrStepFails() {
        final ProcessInstance instance = this.instance("<task id=\"t\"/><sequenceFlow id=\"f\" sourceRef=\"t\" targetRef=\"t\"/>");
        this.withEngine(context -> {
            this.assertFails(context, instance, "missing");
            this.assertFails(context, instance, "f");
            Assertions.assertThrows(TaskException.class, () -> context.getBean(BpmnNodeTask.BEAN_NAME, BpmnNodeTask.class)
                    .execute(UUID.randomUUID()));
        });
    }
}
