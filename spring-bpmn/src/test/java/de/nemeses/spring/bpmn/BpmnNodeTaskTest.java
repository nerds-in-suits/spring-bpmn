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

    private final InMemoryProcessInstanceRepository instances = new InMemoryProcessInstanceRepository();
    private final InMemoryProcessStepRepository steps = new InMemoryProcessStepRepository(this.instances);
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

            BpmnNodeTaskTest.execute(context, task);
            Assertions.assertEquals(List.of("t", "e"), this.triggeredNodes(context));

            BpmnNodeTaskTest.execute(context, this.triggered(context, "e"));
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
            BpmnNodeTaskTest.execute(context, start1);
            BpmnNodeTaskTest.execute(context, this.triggered(context, "e1"));
            Assertions.assertNull(this.endDate(instance));

            BpmnNodeTaskTest.execute(context, start2);
            Assertions.assertNull(this.endDate(instance));
            BpmnNodeTaskTest.execute(context, this.triggered(context, "e2"));
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

            BpmnNodeTaskTest.execute(context, this.triggered(context, "a"));
            final ProcessStep joinA = this.triggered(context).get(2);
            Assertions.assertEquals("ja", joinA.incomingFlowId());
            BpmnNodeTaskTest.execute(context, joinA);
            // parked at the join: still pending, nothing taken
            Assertions.assertNull(this.stored(joinA).endDate());
            Assertions.assertEquals(List.of("a", "b", "join"), this.triggeredNodes(context));

            BpmnNodeTaskTest.execute(context, this.triggered(context, "b"));
            final ProcessStep joinB = this.triggered(context).get(3);
            Assertions.assertEquals("jb", joinB.incomingFlowId());
            BpmnNodeTaskTest.execute(context, joinB);
            Assertions.assertNotNull(this.stored(joinA).endDate());
            Assertions.assertNotNull(this.stored(joinB).endDate());
            Assertions.assertEquals(List.of("a", "b", "join", "join", "e"), this.triggeredNodes(context));

            BpmnNodeTaskTest.execute(context, this.triggered(context, "e"));
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
            BpmnNodeTaskTest.execute(context, a2);
            Assertions.assertEquals(List.of("t"), this.triggeredNodes(context));
            Assertions.assertNotNull(this.stored(a1).endDate());
            Assertions.assertNotNull(this.stored(b1).endDate());
            Assertions.assertNull(this.stored(a2).endDate());

            // the steps completed by the join are skipped, a2 waits for the next step on jb
            BpmnNodeTaskTest.execute(context, a1);
            BpmnNodeTaskTest.execute(context, b1);
            BpmnNodeTaskTest.execute(context, a2);
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
    void exclusiveGatewayTakesDefaultFlow() {
        final ProcessInstance instance = this.instance("<exclusiveGateway id=\"g\" default=\"f2\"/>"
                + "<task id=\"a\"/><task id=\"b\"/>"
                + "<sequenceFlow id=\"f1\" sourceRef=\"g\" targetRef=\"a\"><conditionExpression>${ok}</conditionExpression>"
                + "</sequenceFlow><sequenceFlow id=\"f2\" sourceRef=\"g\" targetRef=\"b\"/>");
        this.withEngine(context -> {
            this.execute(context, instance, "g");
            Assertions.assertEquals(List.of("b"), this.triggeredNodes(context));
            Assertions.assertEquals("f2", this.triggered(context, "b").incomingFlowId());
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
    void ambiguousExclusiveGatewayFails() {
        final ProcessInstance instance = this.instance("<exclusiveGateway id=\"g\"/><task id=\"a\"/><task id=\"b\"/>"
                + "<sequenceFlow id=\"f1\" sourceRef=\"g\" targetRef=\"a\"/>"
                + "<sequenceFlow id=\"f2\" sourceRef=\"g\" targetRef=\"b\"/>");
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
    void conditionalFlowFails() {
        final ProcessInstance instance = this.instance("<task id=\"t\"/><task id=\"a\"/>"
                + "<sequenceFlow id=\"f\" sourceRef=\"t\" targetRef=\"a\"><conditionExpression>${ok}</conditionExpression>"
                + "</sequenceFlow>");
        this.withEngine(context -> {
            this.assertFails(context, instance, "t");
            Assertions.assertTrue(this.tasks.persisted().isEmpty());
        });
    }

    @Test
    void unsupportedNodeTypeFails() {
        final ProcessInstance instance = this.instance("<userTask id=\"u\"/>");
        this.withEngine(context -> this.assertFails(context, instance, "u"));
    }

    @Test
    void loopActivityFails() {
        final ProcessInstance instance = this.instance("<task id=\"t\"><standardLoopCharacteristics/></task>");
        this.withEngine(context -> this.assertFails(context, instance, "t"));
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
