package de.nemeses.spring.bpmn.exec;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.ContextConsumer;

import de.nemeses.spring.bpmn.InMemoryProcessInstanceRepository;
import de.nemeses.spring.bpmn.InMemoryProcessStepRepository;
import de.nemeses.spring.bpmn.ProcessDefinitionAutoConfiguration;
import de.nemeses.spring.bpmn.RecordingTaskPersistence;
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
 * Executes and completes {@code userTask} nodes with in-memory repositories, running the recorded tasks one by one.
 */
class UserTaskTest {

    /** Start, user task "approve", then "yes" if {@code approved}, else (default) "no". */
    private static final String APPROVAL = "<startEvent id=\"s\"/><userTask id=\"approve\" default=\"f3\"/>"
            + "<endEvent id=\"yes\"/><endEvent id=\"no\"/>"
            + "<sequenceFlow id=\"f1\" sourceRef=\"s\" targetRef=\"approve\"/>"
            + "<sequenceFlow id=\"f2\" sourceRef=\"approve\" targetRef=\"yes\">"
            + "<conditionExpression>approved</conditionExpression></sequenceFlow>"
            + "<sequenceFlow id=\"f3\" sourceRef=\"approve\" targetRef=\"no\"/>";

    private final Map<String, ProcessDefinition> definitions = new HashMap<>();
    private final InMemoryProcessInstanceRepository instances = new InMemoryProcessInstanceRepository();
    private final InMemoryProcessStepRepository steps = new InMemoryProcessStepRepository(this.instances);
    private final RecordingTaskPersistence tasks = new RecordingTaskPersistence();
    private int executed;

    private void define(final String flowElements) {
        final String bpmnXml = "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" id=\"d\""
                + " targetNamespace=\"urn:t\"><process id=\"p\">" + flowElements + "</process></definitions>";
        this.definitions.put("p", new ProcessDefinition() {
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
        });
    }

    private void withEngine(final ContextConsumer<AssertableApplicationContext> test) {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ProcessDefinitionAutoConfiguration.class))
                .withUserConfiguration(TaskConfiguration.class)
                .withPropertyValues("spring.tasks.pool-size=0")
                .withBean(ProcessDefinitionService.class,
                        () -> new ProcessEngineServiceTest.MapDefinitionService(this.definitions))
                .withBean(ProcessInstanceRepository.class, () -> this.instances)
                .withBean(ProcessStepRepository.class, () -> this.steps)
                .withBean(TaskPersistence.class, () -> this.tasks)
                .run(test);
    }

    private UUID payload(final AssertableApplicationContext context, final int index) throws TaskException {
        return context.getBean(BpmnNodeTask.BEAN_NAME, BpmnNodeTask.class)
                .deserializePayload(this.tasks.persisted().get(index).payload());
    }

    private void runAll(final AssertableApplicationContext context) throws TaskException {
        while (this.executed < this.tasks.persisted().size()) {
            final UUID stepId = this.payload(context, this.executed);
            this.executed++;
            context.getBean(BpmnNodeTask.BEAN_NAME, BpmnNodeTask.class).execute(stepId);
        }
    }

    private ProcessInstance current(final ProcessInstance instance) throws NoSuchProcessInstanceException {
        return this.instances.retrieve(instance.id());
    }

    private List<String> nodes(final ProcessInstance instance) {
        return this.steps.steps(instance.id()).stream().map(ProcessStep::nodeId).toList();
    }

    @Test
    void userTaskWaitsUntilCompletedWithVariables() {
        this.define(UserTaskTest.APPROVAL);
        this.withEngine(context -> {
            final ProcessEngineService engine = context.getBean(ProcessEngineService.class);
            final ProcessInstance instance = engine.startProcess("p", Map.of("amount", 3));
            this.runAll(context);

            final List<ProcessStep> open = engine.openUserTasks(instance.id());
            Assertions.assertEquals(1, open.size());
            final ProcessStep userTask = open.getFirst();
            Assertions.assertEquals("approve", userTask.nodeId());
            Assertions.assertEquals(instance.id(), userTask.processInstanceId());
            Assertions.assertNotNull(userTask.startDate());
            Assertions.assertNull(userTask.endDate());
            Assertions.assertNull(this.current(instance).endDate(), "the open user task keeps the instance running");
            Assertions.assertEquals(List.of("s", "approve"), this.nodes(instance));

            // a task delivered again keeps waiting
            context.getBean(BpmnNodeTask.BEAN_NAME, BpmnNodeTask.class).execute(userTask.id());
            Assertions.assertNull(this.steps.retrieve(userTask.id()).endDate());
            Assertions.assertEquals(this.executed, this.tasks.persisted().size());

            engine.completeUserTask(userTask.id(), Map.of("approved", true, "amount", 4));
            Assertions.assertNotNull(this.steps.retrieve(userTask.id()).endDate());
            Assertions.assertEquals(Map.of("approved", true, "amount", 4), this.current(instance).variables());
            Assertions.assertEquals(List.of(), engine.openUserTasks(instance.id()));

            this.runAll(context);
            Assertions.assertEquals(List.of("s", "approve", "yes"), this.nodes(instance));
            Assertions.assertNotNull(this.current(instance).endDate());
            Assertions.assertEquals(List.of(), engine.openUserTasks(instance.id()));
        });
    }

    @Test
    void completionWithoutVariablesTakesDefaultFlow() {
        this.define(UserTaskTest.APPROVAL);
        this.withEngine(context -> {
            final ProcessEngineService engine = context.getBean(ProcessEngineService.class);
            final ProcessInstance instance = engine.startProcess("p", Map.of("approved", false));
            // completed before its task ran: the task is skipped later
            final UUID startStep = this.payload(context, 0);
            context.getBean(BpmnNodeTask.BEAN_NAME, BpmnNodeTask.class).execute(startStep);
            this.executed++;
            engine.completeUserTask(engine.openUserTasks(instance.id()).getFirst().id(), null);

            this.runAll(context);
            Assertions.assertEquals(List.of("s", "approve", "no"), this.nodes(instance));
            Assertions.assertEquals(Map.of("approved", false), this.current(instance).variables());
            Assertions.assertNotNull(this.current(instance).endDate());
        });
    }

    @Test
    void parallelUserTasksAreCompletedOneByOne() {
        this.define("<startEvent id=\"s\"/><parallelGateway id=\"fork\"/><userTask id=\"a\"/><userTask id=\"b\"/>"
                + "<parallelGateway id=\"join\"/><endEvent id=\"e\"/>"
                + "<sequenceFlow id=\"f1\" sourceRef=\"s\" targetRef=\"fork\"/>"
                + "<sequenceFlow id=\"f2\" sourceRef=\"fork\" targetRef=\"a\"/>"
                + "<sequenceFlow id=\"f3\" sourceRef=\"fork\" targetRef=\"b\"/>"
                + "<sequenceFlow id=\"f4\" sourceRef=\"a\" targetRef=\"join\"/>"
                + "<sequenceFlow id=\"f5\" sourceRef=\"b\" targetRef=\"join\"/>"
                + "<sequenceFlow id=\"f6\" sourceRef=\"join\" targetRef=\"e\"/>");
        this.withEngine(context -> {
            final ProcessEngineService engine = context.getBean(ProcessEngineService.class);
            final ProcessInstance instance = engine.startProcess("p", Map.of());
            this.runAll(context);
            final List<ProcessStep> open = engine.openUserTasks(instance.id());
            Assertions.assertEquals(List.of("a", "b"), open.stream().map(ProcessStep::nodeId).sorted().toList());

            engine.completeUserTask(open.get(0).id(), Map.of(open.get(0).nodeId(), "done"));
            this.runAll(context);
            Assertions.assertNull(this.current(instance).endDate());
            Assertions.assertEquals(List.of(open.get(1).id()),
                    engine.openUserTasks(instance.id()).stream().map(ProcessStep::id).toList());

            engine.completeUserTask(open.get(1).id(), Map.of(open.get(1).nodeId(), "done"));
            this.runAll(context);
            Assertions.assertNotNull(this.current(instance).endDate());
            Assertions.assertEquals(Map.of("a", "done", "b", "done"), this.current(instance).variables());
        });
    }

    @Test
    void invalidCompletionsFailAndKeepTheTaskOpen() {
        this.define(UserTaskTest.APPROVAL);
        this.withEngine(context -> {
            final ProcessEngineService engine = context.getBean(ProcessEngineService.class);
            final ProcessInstance instance = engine.startProcess("p", Map.of());
            this.runAll(context);
            final ProcessStep userTask = engine.openUserTasks(instance.id()).getFirst();
            final UUID startStep = this.payload(context, 0);

            final ProcessEngineException unknown = Assertions.assertThrows(ProcessEngineException.class,
                    () -> engine.completeUserTask(UUID.randomUUID(), Map.of()));
            Assertions.assertInstanceOf(NoSuchProcessStepException.class, unknown.getCause());
            Assertions.assertThrows(ProcessEngineException.class, () -> engine.completeUserTask(startStep, Map.of()),
                    "not a user task");
            final Map<String, Object> nullValue = new HashMap<>();
            nullValue.put("approved", null);
            Assertions.assertThrows(ProcessEngineException.class,
                    () -> engine.completeUserTask(userTask.id(), nullValue));
            Assertions.assertNull(this.steps.retrieve(userTask.id()).endDate());
            Assertions.assertThrows(ProcessEngineException.class,
                    () -> engine.openUserTasks(UUID.randomUUID()));

            engine.completeUserTask(userTask.id(), Map.of("approved", true));
            Assertions.assertThrows(ProcessEngineException.class,
                    () -> engine.completeUserTask(userTask.id(), Map.of()), "already completed");
        });
    }

    @Test
    void loopCharacteristicsFail() {
        this.define("<startEvent id=\"s\"/><userTask id=\"u\"><standardLoopCharacteristics loopMaximum=\"2\"/>"
                + "</userTask><sequenceFlow id=\"f1\" sourceRef=\"s\" targetRef=\"u\"/>");
        this.withEngine(context -> {
            context.getBean(ProcessEngineService.class).startProcess("p", Map.of());
            final BpmnNodeTask nodeTask = context.getBean(BpmnNodeTask.BEAN_NAME, BpmnNodeTask.class);
            nodeTask.execute(this.payload(context, 0));
            final UUID userTaskStep = this.payload(context, 1);
            Assertions.assertThrows(TaskException.class, () -> nodeTask.execute(userTaskStep));
        });
    }
}
