package de.nemeses.spring.bpmn;

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
 * Executes {@code callActivity} nodes with in-memory repositories, running the recorded tasks one by one.
 */
class CallActivityTest {

    private static final String PARENT_END = "<endEvent id=\"pe\"/>"
            + "<sequenceFlow id=\"pf1\" sourceRef=\"ps\" targetRef=\"call\"/>"
            + "<sequenceFlow id=\"pf2\" sourceRef=\"call\" targetRef=\"pe\"/>";

    private static final String CHILD = "<process id=\"child\"><startEvent id=\"cs\"/>"
            + "<serviceTask id=\"work\" spring:bean=\"doubling\"/><endEvent id=\"ce\"/>"
            + "<sequenceFlow id=\"cf1\" sourceRef=\"cs\" targetRef=\"work\"/>"
            + "<sequenceFlow id=\"cf2\" sourceRef=\"work\" targetRef=\"ce\"/></process>";

    private final Map<String, ProcessDefinition> definitions = new HashMap<>();
    private final InMemoryProcessInstanceRepository instances = new InMemoryProcessInstanceRepository();
    private final InMemoryProcessStepRepository steps = new InMemoryProcessStepRepository(this.instances);
    private final RecordingTaskPersistence tasks = new RecordingTaskPersistence();
    private int executed;

    private static String bpmn(final String... processes) {
        return "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" xmlns:spring=\""
                + BpmnNodeExecutor.NAMESPACE + "\" id=\"d\" targetNamespace=\"urn:t\">" + String.join("", processes)
                + "</definitions>";
    }

    private static String parent(final String callActivity) {
        return CallActivityTest.bpmn("<process id=\"parent\"><startEvent id=\"ps\"/>" + callActivity
                + CallActivityTest.PARENT_END + "</process>");
    }

    private void define(final String name, final String bpmnXml) {
        this.definitions.put(name, new ProcessDefinition() {
            @Override
            public String name() {
                return name;
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
                .withBean(ProcessDefinitionService.class,
                        () -> new ProcessEngineServiceTest.MapDefinitionService(this.definitions))
                .withBean(ProcessInstanceRepository.class, () -> this.instances)
                .withBean(ProcessStepRepository.class, () -> this.steps)
                .withBean(TaskPersistence.class, () -> this.tasks)
                .withBean("doubling", BpmnNodeImplementation.class, () -> context -> {
                    final Object value = context.variables().getOrDefault("value", context.variables().get("amount"));
                    context.setVariable("result", ((Integer) value) * 2);
                    context.setVariable("temp", "child only");
                })
                .run(test);
    }

    /**
     * Executes the next recorded task that was not executed yet.
     *
     * @return the step it executed
     */
    private ProcessStep runNext(final AssertableApplicationContext context)
            throws TaskException, NoSuchProcessStepException {
        final BpmnNodeTask nodeTask = context.getBean(BpmnNodeTask.BEAN_NAME, BpmnNodeTask.class);
        final UUID stepId = nodeTask.deserializePayload(this.tasks.persisted().get(this.executed).payload());
        this.executed++;
        nodeTask.execute(stepId);
        return this.steps.retrieve(stepId);
    }

    private void runAll(final AssertableApplicationContext context) throws TaskException, NoSuchProcessStepException {
        while (this.executed < this.tasks.persisted().size()) {
            this.runNext(context);
        }
    }

    private ProcessInstance current(final ProcessInstance instance) throws NoSuchProcessInstanceException {
        return this.instances.retrieve(instance.id());
    }

    private ProcessInstance child(final ProcessStep callStep) {
        return this.instances.retrieveChild(callStep.id()).orElseThrow();
    }

    @Test
    void parentWaitsForChildAndGetsAllVariablesBack() {
        this.define("parent", CallActivityTest.parent("<callActivity id=\"call\" calledElement=\"child\"/>"));
        this.define("child", CallActivityTest.bpmn(CallActivityTest.CHILD));
        this.withEngine(context -> {
            final ProcessInstance parent = context.getBean(ProcessEngineService.class)
                    .startProcess("parent", Map.of("amount", 3));
            Assertions.assertEquals("ps", this.runNext(context).nodeId());

            final ProcessStep callStep = this.runNext(context);
            Assertions.assertEquals("call", callStep.nodeId());
            Assertions.assertNull(callStep.endDate(), "call activity waits");
            final ProcessInstance child = this.child(callStep);
            Assertions.assertEquals("child", child.processDefinition().name());
            Assertions.assertEquals(callStep.id(), child.parentStep().id());
            Assertions.assertEquals(parent.id(), child.parentInstance().id());
            Assertions.assertNull(parent.parentStep());
            Assertions.assertNull(parent.parentInstance());
            Assertions.assertEquals(Map.of("amount", 3), child.variables());

            Assertions.assertEquals("cs", this.runNext(context).nodeId());
            Assertions.assertEquals("work", this.runNext(context).nodeId());
            Assertions.assertNull(this.steps.retrieve(callStep.id()).endDate(), "still waiting for the child");
            Assertions.assertEquals(Map.of("amount", 3), this.current(parent).variables());

            Assertions.assertEquals("ce", this.runNext(context).nodeId());
            Assertions.assertNotNull(this.current(child).endDate());
            Assertions.assertNull(this.current(parent).endDate());

            // the end of the child triggered the waiting call activity step again
            Assertions.assertEquals(callStep.id(), this.runNext(context).id());
            Assertions.assertNotNull(this.steps.retrieve(callStep.id()).endDate());
            Assertions.assertEquals(Map.of("amount", 3, "result", 6, "temp", "child only"),
                    this.current(parent).variables());

            this.runAll(context);
            Assertions.assertNotNull(this.current(parent).endDate());
            Assertions.assertEquals(List.of("ps", "call", "pe"),
                    this.steps.steps(parent.id()).stream().map(ProcessStep::nodeId).toList());
            Assertions.assertEquals(2, this.instances.created().size());
        });
    }

    @Test
    void mappingsPassOnlyMappedVariables() {
        this.define("parent", CallActivityTest.parent("<callActivity id=\"call\" calledElement=\"child\">"
                + "<extensionElements><spring:in source=\"amount\" target=\"value\"/>"
                + "<spring:in source=\"missing\" target=\"ignored\"/>"
                + "<spring:out source=\"result\" target=\"doubled\"/></extensionElements></callActivity>"));
        this.define("child", CallActivityTest.bpmn(CallActivityTest.CHILD));
        this.withEngine(context -> {
            final ProcessInstance parent = context.getBean(ProcessEngineService.class)
                    .startProcess("parent", Map.of("amount", 3, "other", "o"));
            this.runNext(context);
            final ProcessStep callStep = this.runNext(context);
            final ProcessInstance child = this.child(callStep);
            Assertions.assertEquals(Map.of("value", 3), child.variables());

            this.runAll(context);
            Assertions.assertEquals(Map.of("value", 3, "result", 6, "temp", "child only"),
                    this.current(child).variables());
            Assertions.assertEquals(Map.of("amount", 3, "other", "o", "doubled", 6), this.current(parent).variables());
            Assertions.assertNotNull(this.current(parent).endDate());
        });
    }

    @Test
    void outMappingOnlySwitchesInputToExplicitMode() {
        this.define("parent", CallActivityTest.parent("<callActivity id=\"call\" calledElement=\"child\">"
                + "<extensionElements><spring:out source=\"result\" target=\"doubled\"/></extensionElements>"
                + "</callActivity>"));
        this.define("child", CallActivityTest.bpmn("<process id=\"child\"><startEvent id=\"cs\"/><endEvent id=\"ce\"/>"
                + "<sequenceFlow id=\"cf\" sourceRef=\"cs\" targetRef=\"ce\"/></process>"));
        this.withEngine(context -> {
            final ProcessInstance parent = context.getBean(ProcessEngineService.class)
                    .startProcess("parent", Map.of("amount", 3));
            this.runNext(context);
            final ProcessStep callStep = this.runNext(context);
            Assertions.assertEquals(Map.of(), this.child(callStep).variables());
            this.runAll(context);
            Assertions.assertEquals(Map.of("amount", 3), this.current(parent).variables());
            Assertions.assertNotNull(this.current(parent).endDate());
        });
    }

    @Test
    void redeliveredCallStepStartsNoSecondChild() {
        this.define("parent", CallActivityTest.parent("<callActivity id=\"call\" calledElement=\"child\"/>"));
        this.define("child", CallActivityTest.bpmn(CallActivityTest.CHILD));
        this.withEngine(context -> {
            context.getBean(ProcessEngineService.class).startProcess("parent", Map.of("amount", 3));
            this.runNext(context);
            final ProcessStep callStep = this.runNext(context);
            final int triggered = this.tasks.persisted().size();

            context.getBean(BpmnNodeTask.BEAN_NAME, BpmnNodeTask.class).execute(callStep.id());
            Assertions.assertEquals(2, this.instances.created().size());
            Assertions.assertEquals(triggered, this.tasks.persisted().size());
            Assertions.assertNull(this.steps.retrieve(callStep.id()).endDate());
        });
    }

    @Test
    void calledElementMatchesDefinitionNameAndIgnoresNamespace() {
        this.define("parent", CallActivityTest.bpmn("<process id=\"parent\" xmlns:t=\"urn:t\"><startEvent id=\"ps\"/>"
                + "<callActivity id=\"call\" calledElement=\"t:Shipping\"/>" + CallActivityTest.PARENT_END
                + "</process>"));
        this.define("Shipping", CallActivityTest.bpmn("<process id=\"ship\" name=\"Shipping\"><startEvent id=\"cs\"/>"
                + "</process>"));
        this.withEngine(context -> {
            context.getBean(ProcessEngineService.class).startProcess("parent", Map.of());
            this.runNext(context);
            final ProcessStep callStep = this.runNext(context);
            Assertions.assertEquals("Shipping", this.child(callStep).processDefinition().name());
        });
    }

    @Test
    void missingOrUnknownCalledElementOrInvalidMappingFails() {
        this.define("noElement", CallActivityTest.bpmn("<process id=\"noElement\"><startEvent id=\"ps\"/>"
                + "<callActivity id=\"call\"/>" + CallActivityTest.PARENT_END + "</process>"));
        this.define("unknown", CallActivityTest.bpmn("<process id=\"unknown\"><startEvent id=\"ps\"/>"
                + "<callActivity id=\"call\" calledElement=\"nope\"/>" + CallActivityTest.PARENT_END + "</process>"));
        this.define("invalid", CallActivityTest.bpmn("<process id=\"invalid\"><startEvent id=\"ps\"/>"
                + "<callActivity id=\"call\" calledElement=\"invalid\"><extensionElements>"
                + "<spring:in source=\"amount\"/></extensionElements></callActivity>" + CallActivityTest.PARENT_END
                + "</process>"));
        this.withEngine(context -> {
            for (final String name : List.of("noElement", "unknown", "invalid")) {
                context.getBean(ProcessEngineService.class).startProcess(name, Map.of("amount", 3));
                this.runNext(context);
                final TaskException e = Assertions.assertThrows(TaskException.class, () -> this.runNext(context));
                Assertions.assertInstanceOf(ProcessEngineException.class, e.getCause(), name);
            }
            Assertions.assertEquals(3, this.instances.created().size());
        });
    }
}
