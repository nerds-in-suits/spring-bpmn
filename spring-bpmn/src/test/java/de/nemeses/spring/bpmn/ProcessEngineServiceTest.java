package de.nemeses.spring.bpmn;

import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.ContextConsumer;

import de.nemeses.spring.bpmn.repository.NoSuchProcessDefinitionException;
import de.nemeses.spring.bpmn.repository.ProcessDefinition;
import de.nemeses.spring.bpmn.repository.ProcessDefinitionService;
import de.nemeses.spring.bpmn.repository.ProcessInstance;
import de.nemeses.spring.bpmn.repository.ProcessInstanceRepository;
import de.nemeses.spring.tasks.TaskConfiguration;
import de.nemeses.spring.tasks.TaskPersistence;

class ProcessEngineServiceTest {

    private final Map<String, ProcessDefinition> definitions = new HashMap<>();
    private final InMemoryProcessInstanceRepository instances = new InMemoryProcessInstanceRepository();
    private final RecordingTaskPersistence tasks = new RecordingTaskPersistence();

    private static String bpmn(final String... processes) {
        return "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" id=\"d\" targetNamespace=\"urn:t\">"
                + String.join("", processes) + "</definitions>";
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
                .withBean(ProcessDefinitionService.class, () -> new MapDefinitionService(this.definitions))
                .withBean(ProcessInstanceRepository.class, () -> this.instances)
                .withBean(TaskPersistence.class, () -> this.tasks)
                .run(test);
    }

    @Test
    void startByNameCreatesInstanceAndSchedulesEachStartEvent() {
        this.define("order", ProcessEngineServiceTest.bpmn(
                "<process id=\"order\"><startEvent id=\"s1\"/><startEvent id=\"s2\"/><endEvent id=\"e\"/></process>"));
        this.withEngine(context -> {
            final ProcessInstance instance = context.getBean(ProcessEngineService.class)
                    .startProcess("order", Map.of("amount", 3));

            Assertions.assertEquals(List.of(instance), this.instances.created());
            Assertions.assertSame(this.definitions.get("order"), instance.processDefinition());
            Assertions.assertEquals(Map.of("amount", 3), instance.variables());

            final BpmnNodeTask nodeTask = context.getBean(BpmnNodeTask.BEAN_NAME, BpmnNodeTask.class);
            Assertions.assertEquals(List.of(BpmnNodeTask.BEAN_NAME, BpmnNodeTask.BEAN_NAME),
                    this.tasks.persisted().stream().map(RecordingTaskPersistence.Recorded::beanName).toList());
            Assertions.assertEquals(List.of(new NodeTaskPayload(instance.id(), "s1"), new NodeTaskPayload(instance.id(), "s2")),
                    this.tasks.persisted().stream().map(task -> nodeTask.deserializePayload(task.payload())).toList());
        });
    }

    @Test
    void startUsesProcessMatchingDefinitionName() {
        this.define("Second", ProcessEngineServiceTest.bpmn("<process id=\"a\"><startEvent id=\"sa\"/></process>",
                "<process id=\"b\" name=\"Second\"><startEvent id=\"sb\"/></process>"));
        this.withEngine(context -> {
            context.getBean(ProcessEngineService.class).startProcess(this.definitions.get("Second"), null);

            Assertions.assertEquals(1, this.tasks.persisted().size());
            Assertions.assertTrue(this.tasks.persisted().get(0).payload().contains("\"sb\""));
            Assertions.assertEquals(Map.of(), this.instances.created().get(0).variables());
        });
    }

    @Test
    void startUnknownDefinitionFails() {
        this.withEngine(context -> {
            final ProcessEngineException e = Assertions.assertThrows(ProcessEngineException.class,
                    () -> context.getBean(ProcessEngineService.class).startProcess("missing", Map.of()));
            Assertions.assertInstanceOf(NoSuchProcessDefinitionException.class, e.getCause());
            this.assertNothingCreated();
        });
    }

    @Test
    void startWithoutStartEventFails() {
        this.define("empty", ProcessEngineServiceTest.bpmn("<process id=\"empty\"><endEvent id=\"e\"/></process>"));
        this.withEngine(context -> {
            Assertions.assertThrows(ProcessEngineException.class,
                    () -> context.getBean(ProcessEngineService.class).startProcess("empty", Map.of()));
            this.assertNothingCreated();
        });
    }

    @Test
    void startWithoutMatchingProcessFails() {
        this.define("other", ProcessEngineServiceTest.bpmn("<process id=\"p\"><startEvent id=\"s\"/></process>"));
        this.withEngine(context -> {
            Assertions.assertThrows(ProcessEngineException.class,
                    () -> context.getBean(ProcessEngineService.class).startProcess("other", Map.of()));
            this.assertNothingCreated();
        });
    }

    @Test
    void startInvalidDocumentFails() {
        this.define("broken", "<nope/>");
        this.withEngine(context -> {
            final ProcessEngineException e = Assertions.assertThrows(ProcessEngineException.class,
                    () -> context.getBean(ProcessEngineService.class).startProcess("broken", Map.of()));
            Assertions.assertInstanceOf(BpmnParseException.class, e.getCause());
            this.assertNothingCreated();
        });
    }

    private void assertNothingCreated() {
        Assertions.assertTrue(this.instances.created().isEmpty());
        Assertions.assertTrue(this.tasks.persisted().isEmpty());
    }

    private record MapDefinitionService(Map<String, ProcessDefinition> definitions) implements ProcessDefinitionService {

        @Override
        public void register(final ProcessDefinition definition) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ProcessDefinition retrieveLatest(final String name) throws NoSuchProcessDefinitionException {
            final ProcessDefinition definition = this.definitions.get(name);
            if (definition == null) {
                throw new NoSuchProcessDefinitionException(name);
            }
            return definition;
        }

        @Override
        public Collection<ProcessDefinition> retrieveAll(final String name) {
            return this.definitions.containsKey(name) ? List.of(this.definitions.get(name)) : List.of();
        }
    }
}
