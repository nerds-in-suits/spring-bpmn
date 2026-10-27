package de.nemeses.spring.bpmn;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import de.nemeses.spring.bpmn.repository.NoSuchProcessDefinitionException;
import de.nemeses.spring.bpmn.repository.ProcessDefinition;
import de.nemeses.spring.bpmn.repository.ProcessDefinitionService;
import de.nemeses.spring.bpmn.repository.ProcessInstanceRepository;
import de.nemeses.spring.bpmn.repository.ProcessStepRepository;
import de.nemeses.spring.tasks.TaskConfiguration;
import de.nemeses.spring.tasks.TaskPersistence;

class ProcessDefinitionRegistrarTest {

    private final InMemoryService service = new InMemoryService();

    private BpmnParser parser;

    @BeforeEach
    void setUp() throws BpmnParseException {
        this.parser = new BpmnParser();
    }

    private static Resource bpmn(final String... processes) {
        final StringBuilder xml = new StringBuilder(
                "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" id=\"d\" targetNamespace=\"urn:t\">");
        for (final String process : processes) {
            xml.append(process);
        }
        return new ByteArrayResource(xml.append("</definitions>").toString().getBytes(StandardCharsets.UTF_8));
    }

    private ProcessDefinitionRegistrar registrar(final Resource... resources) {
        return new ProcessDefinitionRegistrar(this.parser, this.service, List.of(() -> Arrays.stream(resources)));
    }

    @Test
    void registersNewResource() throws Exception {
        this.registrar(ProcessDefinitionRegistrarTest.bpmn("<process id=\"a\"><startEvent id=\"s\"/></process>")).registerAll();
        Assertions.assertEquals(1, this.service.all.size());
        Assertions.assertEquals("a", this.service.retrieveLatest("a").name());
    }

    @Test
    void usesNameAttributeBeforeId() throws Exception {
        this.registrar(ProcessDefinitionRegistrarTest.bpmn("<process id=\"a\" name=\"Order\"/>")).registerAll();
        Assertions.assertEquals("Order", this.service.retrieveLatest("Order").name());
    }

    @Test
    void skipsUnchangedHash() throws Exception {
        final Resource resource = ProcessDefinitionRegistrarTest.bpmn("<process id=\"a\"/>");
        this.registrar(resource).registerAll();
        this.registrar(resource).registerAll();
        Assertions.assertEquals(1, this.service.all.size());
    }

    @Test
    void registersChangedHashAgain() throws Exception {
        this.registrar(ProcessDefinitionRegistrarTest.bpmn("<process id=\"a\"/>")).registerAll();
        this.registrar(ProcessDefinitionRegistrarTest.bpmn("<process id=\"a\"><startEvent id=\"s\"/></process>")).registerAll();
        Assertions.assertEquals(2, this.service.all.size());
        Assertions.assertTrue(this.service.retrieveLatest("a").bpmnXml().contains("startEvent"));
    }

    @Test
    void registersEachProcessOfDocumentSeparately() throws Exception {
        this.registrar(ProcessDefinitionRegistrarTest.bpmn("<process id=\"a\"/>", "<process id=\"b\"/>")).registerAll();
        Assertions.assertEquals(2, this.service.all.size());
        Assertions.assertEquals(this.service.retrieveLatest("a").md5Hash(), this.service.retrieveLatest("b").md5Hash());
    }

    @Test
    void rejectsInvalidDocument() {
        final IllegalStateException e = Assertions.assertThrows(IllegalStateException.class,
                () -> this.registrar(new ByteArrayResource("<nope/>".getBytes(StandardCharsets.UTF_8))).registerAll());
        Assertions.assertInstanceOf(BpmnParseException.class, e.getCause());
    }

    @Test
    void md5HashIsHexOfContent() {
        Assertions.assertEquals("d41d8cd98f00b204e9800998ecf8427e",
                ProcessDefinitionRegistrarTest.definition("x", null, "").md5Hash());
    }

    @Test
    void autoConfigurationRegistersLocationsAndSources() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ProcessDefinitionAutoConfiguration.class))
                .withUserConfiguration(TaskConfiguration.class)
                .withBean(ProcessDefinitionService.class, () -> this.service)
                .withBean(ProcessInstanceRepository.class, InMemoryProcessInstanceRepository::new)
                .withBean(ProcessStepRepository.class,
                        () -> new InMemoryProcessStepRepository(new InMemoryProcessInstanceRepository()))
                .withBean(TaskPersistence.class, RecordingTaskPersistence::new)
                .withBean(ProcessDefinitionSource.class,
                        () -> () -> List.of(ProcessDefinitionRegistrarTest.bpmn("<process id=\"fromSource\"/>")).stream())
                .run(context -> {
                    context.getBean(ApplicationRunner.class).run(null);
                    Assertions.assertNotNull(this.service.retrieveLatest("defaultProcess"));
                    Assertions.assertNotNull(this.service.retrieveLatest("fromSource"));
                });
    }

    @Test
    void locationsAreConfigurable() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ProcessDefinitionAutoConfiguration.class))
                .withUserConfiguration(TaskConfiguration.class)
                .withBean(ProcessDefinitionService.class, () -> this.service)
                .withBean(ProcessInstanceRepository.class, InMemoryProcessInstanceRepository::new)
                .withBean(ProcessStepRepository.class,
                        () -> new InMemoryProcessStepRepository(new InMemoryProcessInstanceRepository()))
                .withBean(TaskPersistence.class, RecordingTaskPersistence::new)
                .withPropertyValues("spring.bpmn.process-definition-locations=classpath:/bpmn/simple.bpmn")
                .run(context -> {
                    context.getBean(ApplicationRunner.class).run(null);
                    Assertions.assertEquals(List.of("p1"), this.service.all.stream().map(ProcessDefinition::name).toList());
                });
    }

    private static ProcessDefinition definition(final String name, final Date createdDate, final String bpmnXml) {
        return new ProcessDefinition() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public Date createdDate() {
                return createdDate;
            }

            @Override
            public String bpmnXml() {
                return bpmnXml;
            }
        };
    }

    private static final class InMemoryService implements ProcessDefinitionService {

        private final List<ProcessDefinition> all = new ArrayList<>();

        @Override
        public void register(final ProcessDefinition definition) {
            this.all.add(0, ProcessDefinitionRegistrarTest.definition(definition.name(), new Date(), definition.bpmnXml()));
        }

        @Override
        public ProcessDefinition retrieveLatest(final String name) throws NoSuchProcessDefinitionException {
            return this.all.stream().filter(d -> d.name().equals(name)).findFirst()
                    .orElseThrow(() -> new NoSuchProcessDefinitionException(name));
        }

        @Override
        public Collection<ProcessDefinition> retrieveAll(final String name) {
            return this.all.stream().filter(d -> d.name().equals(name)).toList();
        }
    }
}
