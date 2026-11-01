package de.nemeses.spring.bpmn.exec;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.ContextConsumer;

import de.nemeses.spring.bpmn.BpmnParseException;
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
 * Executes multi-instance activities ({@code loop.MultiInstanceLoop}) step by step through the {@link BpmnNodeExecutor}
 * with in-memory repositories and recorded tasks.
 */
class MultiInstanceLoopTest {

    private static final String DEFINITIONS = "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" "
            + "xmlns:spring=\"" + BpmnNodeExecutor.NAMESPACE + "\" id=\"definitions\" targetNamespace=\"urn:t\">"
            + "<process id=\"p\">";

    private final InMemoryProcessInstanceRepository instances = new InMemoryProcessInstanceRepository();
    private final InMemoryProcessStepRepository steps = new InMemoryProcessStepRepository(this.instances);
    private final RecordingTaskPersistence tasks = new RecordingTaskPersistence();
    private final List<BpmnNodeContext> calls = new ArrayList<>();

    private ProcessInstance instance(final Map<String, Object> variables, final String processContent) {
        final String bpmnXml = DEFINITIONS + processContent + "</process></definitions>";
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
        }, variables);
    }

    /**
     * @return an instance with a service task {@code mi} calling the bean with the loop, followed by end event
     *         {@code e}
     */
    private ProcessInstance serviceTask(final Map<String, Object> variables, final String bean, final String loop) {
        return this.instance(variables, "<serviceTask id=\"mi\" spring:bean=\"" + bean + "\">" + loop
                + "</serviceTask><endEvent id=\"e\"/><sequenceFlow id=\"f\" sourceRef=\"mi\" targetRef=\"e\"/>");
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
                .withBean("counting", BpmnNodeImplementation.class, () -> context -> {
                    context.setVariable("count", ((Integer) context.variables().getOrDefault("count", 0)) + 1);
                    this.calls.add(context);
                })
                .withBean("failing", BpmnNodeImplementation.class, () -> context -> {
                    throw new ProcessEngineException("failed");
                })
                .run(test);
    }

    private ProcessStep execute(final AssertableApplicationContext context, final ProcessInstance instance,
            final String nodeId) throws TaskException, NoSuchProcessInstanceException {
        final ProcessStep step = this.steps.create(instance.id(), nodeId, null, null);
        MultiInstanceLoopTest.execute(context, step);
        return step;
    }

    private static void execute(final AssertableApplicationContext context, final ProcessStep step)
            throws TaskException {
        context.getBean(BpmnNodeExecutor.BEAN_NAME, BpmnNodeExecutor.class).execute(step.id());
    }

    private void assertFails(final AssertableApplicationContext context, final ProcessInstance instance,
            final String nodeId) throws NoSuchProcessInstanceException {
        final ProcessStep step = this.steps.create(instance.id(), nodeId, null, null);
        final TaskException e = Assertions.assertThrows(TaskException.class,
                () -> MultiInstanceLoopTest.execute(context, step));
        Assertions.assertInstanceOf(ProcessEngineException.class, e.getCause());
        Assertions.assertFalse(e.getCause().getCause() instanceof BpmnParseException, nodeId);
    }

    /**
     * @return the steps of all triggered tasks, in trigger order, as stored now
     */
    private List<ProcessStep> triggered(final AssertableApplicationContext context) {
        try {
            final BpmnNodeExecutor nodeTask = context.getBean(BpmnNodeExecutor.BEAN_NAME, BpmnNodeExecutor.class);
            final List<ProcessStep> result = new ArrayList<>();
            for (final RecordingTaskPersistence.Recorded task : this.tasks.persisted()) {
                result.add(this.steps.retrieve(nodeTask.deserializePayload(task.payload())));
            }
            return result;
        } catch (final NoSuchProcessStepException e) {
            throw new AssertionError(e);
        }
    }

    private List<ProcessStep> triggered(final AssertableApplicationContext context, final String nodeId) {
        return this.triggered(context).stream().filter(step -> step.nodeId().equals(nodeId)).toList();
    }

    private ProcessStep stored(final ProcessStep step) throws NoSuchProcessStepException {
        return this.steps.retrieve(step.id());
    }

    private Map<String, Object> variables(final ProcessInstance instance) throws NoSuchProcessInstanceException {
        return this.instances.retrieve(instance.id()).variables();
    }

    private List<Integer> loopCounters() {
        return this.calls.stream().map(BpmnNodeContext::loopCounter).toList();
    }

    @Test
    void parallelLoopCreatesAllInstanceStepsAndContinuesOnceAfterTheLast() {
        final ProcessInstance instance = this.serviceTask(Map.of("amount", 3), "recording",
                "<multiInstanceLoopCharacteristics><loopCardinality>${amount}</loopCardinality>"
                        + "</multiInstanceLoopCharacteristics>");
        this.withEngine(context -> {
            final ProcessStep activity = this.execute(context, instance, "mi");

            final List<ProcessStep> created = this.triggered(context, "mi");
            Assertions.assertEquals(List.of(0, 1, 2), created.stream().map(ProcessStep::loopCounter).toList());
            Assertions.assertTrue(created.stream().allMatch(step -> activity.id().equals(step.parentStepId())
                    && step.loopItem() == null && step.endDate() == null));
            Assertions.assertEquals(3, this.stored(activity).loopCounter());
            Assertions.assertNull(this.stored(activity).endDate());
            Assertions.assertTrue(this.calls.isEmpty());

            // instances may run in any order
            MultiInstanceLoopTest.execute(context, created.get(2));
            MultiInstanceLoopTest.execute(context, created.get(0));
            Assertions.assertTrue(this.triggered(context, "e").isEmpty());
            Assertions.assertNull(this.stored(activity).endDate());

            MultiInstanceLoopTest.execute(context, created.get(1));
            Assertions.assertEquals(List.of(2, 0, 1), this.loopCounters());
            Assertions.assertNotNull(this.stored(activity).endDate());
            final List<ProcessStep> end = this.triggered(context, "e");
            Assertions.assertEquals(1, end.size());
            Assertions.assertNull(end.get(0).parentStepId());
            Assertions.assertEquals("f", end.get(0).incomingFlowId());

            // tasks delivered again change nothing
            MultiInstanceLoopTest.execute(context, created.get(1));
            MultiInstanceLoopTest.execute(context, activity);
            Assertions.assertEquals(3, this.calls.size());
            Assertions.assertEquals(4, this.tasks.persisted().size());

            MultiInstanceLoopTest.execute(context, end.get(0));
            Assertions.assertNotNull(this.instances.retrieve(instance.id()).endDate());
            Assertions.assertEquals(Map.of("amount", 3), this.variables(instance));
        });
    }

    @Test
    void sequentialLoopOverCollectionCreatesStepsOneAfterAnother() {
        final ProcessInstance instance = this.serviceTask(Map.of("items", List.of("a", "b", "c"), "item", "kept"),
                "recording", "<multiInstanceLoopCharacteristics isSequential=\"true\">"
                        + "<loopDataInputRef>items</loopDataInputRef><inputDataItem id=\"i\" name=\"item\"/>"
                        + "</multiInstanceLoopCharacteristics>");
        this.withEngine(context -> {
            final ProcessStep activity = this.execute(context, instance, "mi");
            Assertions.assertEquals(List.of("a", "b", "c"), this.stored(activity).loopItem());

            for (int index = 0; index < 3; index++) {
                final List<ProcessStep> created = this.triggered(context, "mi");
                Assertions.assertEquals(index + 1, created.size());
                final ProcessStep current = created.get(index);
                Assertions.assertEquals(index, current.loopCounter());
                Assertions.assertTrue(this.triggered(context, "e").isEmpty());
                MultiInstanceLoopTest.execute(context, current);
            }

            Assertions.assertEquals(List.of(0, 1, 2), this.loopCounters());
            Assertions.assertEquals(List.of("a", "b", "c"),
                    this.calls.stream().map(BpmnNodeContext::loopItem).toList());
            Assertions.assertEquals(List.of("a", "b", "c"),
                    this.calls.stream().map(call -> call.variables().get("item")).toList());
            Assertions.assertEquals(1, this.triggered(context, "e").size());
            Assertions.assertNotNull(this.stored(activity).endDate());
            // the input data item is local to the instances
            Assertions.assertEquals(Map.of("items", List.of("a", "b", "c"), "item", "kept"), this.variables(instance));
        });
    }

    @Test
    void instancesChangeProcessVariablesOneAfterTheOther() {
        final ProcessInstance instance = this.serviceTask(Map.of(), "counting",
                "<multiInstanceLoopCharacteristics><loopCardinality>3</loopCardinality>"
                        + "</multiInstanceLoopCharacteristics>");
        this.withEngine(context -> {
            this.execute(context, instance, "mi");
            final List<ProcessStep> created = this.triggered(context, "mi");

            MultiInstanceLoopTest.execute(context, created.get(0));
            Assertions.assertEquals(Map.of("count", 1), this.variables(instance));
            MultiInstanceLoopTest.execute(context, created.get(1));
            MultiInstanceLoopTest.execute(context, created.get(2));
            Assertions.assertEquals(Map.of("count", 3), this.variables(instance));
        });
    }

    @Test
    void completionConditionCompletesPendingInstancesOfParallelLoop() {
        final ProcessInstance instance = this.serviceTask(Map.of(), "recording",
                "<multiInstanceLoopCharacteristics><loopCardinality>4</loopCardinality>"
                        + "<completionCondition>numberOfCompletedInstances &gt;= 2 and numberOfInstances == 4"
                        + " and numberOfActiveInstances == 2 and numberOfTerminatedInstances == 0"
                        + "</completionCondition></multiInstanceLoopCharacteristics>");
        this.withEngine(context -> {
            final ProcessStep activity = this.execute(context, instance, "mi");
            final List<ProcessStep> created = this.triggered(context, "mi");

            MultiInstanceLoopTest.execute(context, created.get(3));
            Assertions.assertTrue(this.triggered(context, "e").isEmpty());
            MultiInstanceLoopTest.execute(context, created.get(1));

            Assertions.assertEquals(1, this.triggered(context, "e").size());
            Assertions.assertNotNull(this.stored(activity).endDate());
            Assertions.assertTrue(this.triggered(context, "mi").stream().allMatch(step -> step.endDate() != null));

            // the tasks of the instances completed without running are skipped
            MultiInstanceLoopTest.execute(context, created.get(0));
            MultiInstanceLoopTest.execute(context, created.get(2));
            Assertions.assertEquals(List.of(3, 1), this.loopCounters());
            Assertions.assertEquals(1, this.triggered(context, "e").size());
        });
    }

    @Test
    void completionConditionStopsSequentialLoop() {
        final ProcessInstance instance = this.serviceTask(Map.of(), "recording",
                "<multiInstanceLoopCharacteristics isSequential=\"true\"><loopCardinality>5</loopCardinality>"
                        + "<completionCondition>${loopCounter == 1}</completionCondition>"
                        + "</multiInstanceLoopCharacteristics>");
        this.withEngine(context -> {
            this.execute(context, instance, "mi");
            MultiInstanceLoopTest.execute(context, this.triggered(context, "mi").get(0));
            MultiInstanceLoopTest.execute(context, this.triggered(context, "mi").get(1));

            Assertions.assertEquals(List.of(0, 1), this.loopCounters());
            Assertions.assertEquals(2, this.triggered(context, "mi").size());
            Assertions.assertEquals(1, this.triggered(context, "e").size());
        });
    }

    @Test
    void outputDataItemsAreCollectedInIndexOrder() {
        final ProcessInstance instance = this.instance(Map.of("items", List.of("a", "b", "c")),
                "<scriptTask id=\"mi\"><multiInstanceLoopCharacteristics>"
                        + "<loopDataInputRef>items</loopDataInputRef><loopDataOutputRef>results</loopDataOutputRef>"
                        + "<inputDataItem id=\"item\"/><outputDataItem id=\"o\" name=\"result\"/>"
                        + "</multiInstanceLoopCharacteristics><script>result = item + '!'; seen = item</script>"
                        + "</scriptTask>");
        this.withEngine(context -> {
            this.execute(context, instance, "mi");
            final List<ProcessStep> created = this.triggered(context, "mi");
            Assertions.assertEquals(List.of("a", "b", "c"), created.stream().map(ProcessStep::loopItem).toList());

            MultiInstanceLoopTest.execute(context, created.get(2));
            Assertions.assertEquals(Map.of("2", "c!"), this.variables(instance).get("mi.loopOutputs"));
            MultiInstanceLoopTest.execute(context, created.get(0));
            MultiInstanceLoopTest.execute(context, created.get(1));

            // other variable changes are stored, the data items are local to the instances
            Assertions.assertEquals(Map.of("items", List.of("a", "b", "c"), "results", List.of("a!", "b!", "c!"),
                    "seen", "b"), this.variables(instance));
        });
    }

    @Test
    void outputsContainOnlyCompletedInstances() {
        final ProcessInstance instance = this.instance(Map.of("items", List.of(1, 2, 3)),
                "<scriptTask id=\"mi\"><multiInstanceLoopCharacteristics isSequential=\"true\">"
                        + "<loopDataInputRef>items</loopDataInputRef><loopDataOutputRef>results</loopDataOutputRef>"
                        + "<inputDataItem id=\"item\"/><outputDataItem id=\"result\"/>"
                        + "<completionCondition>numberOfCompletedInstances == 2</completionCondition>"
                        + "</multiInstanceLoopCharacteristics><script>result = item * 10</script></scriptTask>");
        this.withEngine(context -> {
            this.execute(context, instance, "mi");
            MultiInstanceLoopTest.execute(context, this.triggered(context, "mi").get(0));
            MultiInstanceLoopTest.execute(context, this.triggered(context, "mi").get(1));

            Assertions.assertEquals(Map.of("items", List.of(1, 2, 3), "results", List.of(10, 20)),
                    this.variables(instance));
        });
    }

    @Test
    void loopWithoutInstancesContinuesAtOnce() {
        final ProcessInstance instance = this.serviceTask(Map.of("items", List.of()), "recording",
                "<multiInstanceLoopCharacteristics><loopDataInputRef>items</loopDataInputRef>"
                        + "<loopDataOutputRef>results</loopDataOutputRef><outputDataItem id=\"result\"/>"
                        + "</multiInstanceLoopCharacteristics>");
        this.withEngine(context -> {
            final ProcessStep activity = this.execute(context, instance, "mi");

            Assertions.assertTrue(this.calls.isEmpty());
            Assertions.assertTrue(this.triggered(context, "mi").isEmpty());
            Assertions.assertEquals(1, this.triggered(context, "e").size());
            Assertions.assertNotNull(this.stored(activity).endDate());
            Assertions.assertEquals(0, this.stored(activity).loopCounter());
            Assertions.assertEquals(Map.of("items", List.of(), "results", List.of()), this.variables(instance));
        });
    }

    @Test
    void plainTaskLoopInsideSubProcessContinuesInItsScope() {
        final ProcessInstance instance = this.instance(Map.of(), "<subProcess id=\"sp\"><startEvent id=\"ss\"/>"
                + "<task id=\"mi\"><multiInstanceLoopCharacteristics><loopCardinality>2</loopCardinality>"
                + "</multiInstanceLoopCharacteristics></task><endEvent id=\"se\"/>"
                + "<sequenceFlow id=\"i1\" sourceRef=\"ss\" targetRef=\"mi\"/>"
                + "<sequenceFlow id=\"i2\" sourceRef=\"mi\" targetRef=\"se\"/></subProcess>"
                + "<endEvent id=\"e\"/><sequenceFlow id=\"f\" sourceRef=\"sp\" targetRef=\"e\"/>");
        this.withEngine(context -> {
            final ProcessStep subProcess = this.execute(context, instance, "sp");
            MultiInstanceLoopTest.execute(context, this.triggered(context, "ss").get(0));
            final ProcessStep activity = this.triggered(context, "mi").get(0);
            Assertions.assertEquals(subProcess.id(), activity.parentStepId());
            MultiInstanceLoopTest.execute(context, activity);

            final List<ProcessStep> created = this.triggered(context, "mi").subList(1, 3);
            Assertions.assertTrue(created.stream().allMatch(step -> activity.id().equals(step.parentStepId())));
            MultiInstanceLoopTest.execute(context, created.get(0));
            MultiInstanceLoopTest.execute(context, created.get(1));

            final ProcessStep end = this.triggered(context, "se").get(0);
            Assertions.assertEquals(subProcess.id(), end.parentStepId());
            Assertions.assertNull(this.stored(subProcess).endDate());
            MultiInstanceLoopTest.execute(context, end);
            Assertions.assertNotNull(this.stored(subProcess).endDate());
            Assertions.assertEquals(1, this.triggered(context, "e").size());
        });
    }

    @Test
    void manualTaskLoopPassesThroughEveryInstance() {
        final ProcessInstance instance = this.instance(Map.of("items", List.of("a", "b")),
                "<manualTask id=\"mi\"><multiInstanceLoopCharacteristics isSequential=\"true\">"
                        + "<loopDataInputRef>items</loopDataInputRef><inputDataItem id=\"i\" name=\"item\"/>"
                        + "</multiInstanceLoopCharacteristics></manualTask><endEvent id=\"e\"/>"
                        + "<sequenceFlow id=\"f\" sourceRef=\"mi\" targetRef=\"e\"/>");
        this.withEngine(context -> {
            final ProcessStep activity = this.execute(context, instance, "mi");
            final ProcessStep first = this.triggered(context, "mi").get(0);
            Assertions.assertEquals(activity.id(), first.parentStepId());
            Assertions.assertEquals(0, first.loopCounter());
            MultiInstanceLoopTest.execute(context, first);
            Assertions.assertNotNull(this.stored(first).endDate());
            Assertions.assertTrue(this.triggered(context, "e").isEmpty());

            final ProcessStep second = this.triggered(context, "mi").get(1);
            Assertions.assertEquals(1, second.loopCounter());
            MultiInstanceLoopTest.execute(context, second);
            Assertions.assertNotNull(this.stored(activity).endDate());
            Assertions.assertEquals(1, this.triggered(context, "e").size());
            Assertions.assertEquals(Map.of("items", List.of("a", "b")), this.variables(instance));
        });
    }

    @Test
    void pendingLoopKeepsInstanceRunning() {
        final ProcessInstance instance = this.instance(Map.of(), "<parallelGateway id=\"fork\"/>"
                + "<task id=\"mi\"><multiInstanceLoopCharacteristics><loopCardinality>1</loopCardinality>"
                + "</multiInstanceLoopCharacteristics></task><endEvent id=\"e\"/>"
                + "<sequenceFlow id=\"f1\" sourceRef=\"fork\" targetRef=\"mi\"/>"
                + "<sequenceFlow id=\"f2\" sourceRef=\"fork\" targetRef=\"e\"/>");
        this.withEngine(context -> {
            this.execute(context, instance, "fork");
            MultiInstanceLoopTest.execute(context, this.triggered(context, "mi").get(0));
            MultiInstanceLoopTest.execute(context, this.triggered(context, "e").get(0));
            Assertions.assertNull(this.instances.retrieve(instance.id()).endDate());

            MultiInstanceLoopTest.execute(context, this.triggered(context, "mi").get(1));
            Assertions.assertNull(this.instances.retrieve(instance.id()).endDate());
            Assertions.assertEquals(0, this.steps.countPending(instance.id()));
        });
    }

    @Test
    void failingInstanceFails() {
        final ProcessInstance instance = this.serviceTask(Map.of(), "failing",
                "<multiInstanceLoopCharacteristics><loopCardinality>1</loopCardinality>"
                        + "</multiInstanceLoopCharacteristics>");
        this.withEngine(context -> {
            this.execute(context, instance, "mi");
            final ProcessStep created = this.triggered(context, "mi").get(0);
            final TaskException e = Assertions.assertThrows(TaskException.class,
                    () -> MultiInstanceLoopTest.execute(context, created));
            Assertions.assertInstanceOf(ProcessEngineException.class, e.getCause());
            Assertions.assertTrue(this.triggered(context, "e").isEmpty());
        });
    }

    @Test
    void invalidMultiInstanceLoopsFail() {
        final Map<String, String> loops = new LinkedHashMap<>();
        loops.put("neither", "");
        loops.put("both", "<loopCardinality>1</loopCardinality><loopDataInputRef>items</loopDataInputRef>");
        loops.put("negative", "<loopCardinality>-1</loopCardinality>");
        loops.put("noInteger", "<loopCardinality>'x'</loopCardinality>");
        loops.put("decimal", "<loopCardinality>1.5</loopCardinality>");
        loops.put("unknownVariable", "<loopCardinality>missing</loopCardinality>");
        loops.put("noCollection", "<loopDataInputRef>amount</loopDataInputRef>");
        loops.put("missingCollection", "<loopDataInputRef>missing</loopDataInputRef>");
        loops.put("inputItemWithoutCollection", "<loopCardinality>1</loopCardinality><inputDataItem id=\"in\"/>");
        loops.put("outputItemWithoutRef", "<loopCardinality>1</loopCardinality><outputDataItem id=\"out\"/>");
        loops.put("outputRefWithoutItem", "<loopCardinality>1</loopCardinality>"
                + "<loopDataOutputRef>results</loopDataOutputRef>");
        final StringBuilder content = new StringBuilder();
        loops.forEach((id, loop) -> content.append("<task id=\"").append(id).append("\">")
                .append("<multiInstanceLoopCharacteristics>").append(loop).append("</multiInstanceLoopCharacteristics>")
                .append("</task>"));
        content.append("<task id=\"behavior\"><multiInstanceLoopCharacteristics behavior=\"One\">"
                + "<loopCardinality>1</loopCardinality></multiInstanceLoopCharacteristics></task>");
        content.append("<task id=\"nonBooleanCompletion\"><multiInstanceLoopCharacteristics>"
                + "<loopCardinality>2</loopCardinality><completionCondition>amount</completionCondition>"
                + "</multiInstanceLoopCharacteristics></task>");
        content.append("<subProcess id=\"sp\"><multiInstanceLoopCharacteristics><loopCardinality>1</loopCardinality>"
                + "</multiInstanceLoopCharacteristics><startEvent id=\"ss\"/></subProcess>");
        content.append("<userTask id=\"u\"><multiInstanceLoopCharacteristics><loopCardinality>1</loopCardinality>"
                + "</multiInstanceLoopCharacteristics></userTask>");
        final ProcessInstance instance = this.instance(Map.of("amount", 3), content.toString());
        this.withEngine(context -> {
            for (final String id : loops.keySet()) {
                this.assertFails(context, instance, id);
            }
            this.assertFails(context, instance, "behavior");
            this.assertFails(context, instance, "u");
            Assertions.assertTrue(this.tasks.persisted().isEmpty());

            // the loop is generic: an activity that cannot run an instance in one step fails at its instance
            this.execute(context, instance, "sp");
            final List<ProcessStep> subProcessInstances = this.triggered(context, "sp");
            Assertions.assertEquals(1, subProcessInstances.size());
            Assertions.assertThrows(TaskException.class,
                    () -> MultiInstanceLoopTest.execute(context, subProcessInstances.get(0)));

            // the completion condition is evaluated after the instance
            this.execute(context, instance, "nonBooleanCompletion");
            final TaskException e = Assertions.assertThrows(TaskException.class,
                    () -> MultiInstanceLoopTest.execute(context, this.triggered(context, "nonBooleanCompletion").get(0)));
            Assertions.assertInstanceOf(ProcessEngineException.class, e.getCause());
        });
    }
}
