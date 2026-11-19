package de.nemeses.spring.bpmn.rest;

import java.time.Duration;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;

import org.awaitility.Awaitility;
import org.springframework.stereotype.Component;

import de.nemeses.spring.bpmn.exec.ProcessEngineException;
import de.nemeses.spring.bpmn.exec.ProcessEngineService;
import de.nemeses.spring.bpmn.repository.NoSuchProcessInstanceException;
import de.nemeses.spring.bpmn.repository.ProcessDefinition;
import de.nemeses.spring.bpmn.repository.ProcessDefinitionService;
import de.nemeses.spring.bpmn.repository.ProcessInstance;
import de.nemeses.spring.bpmn.repository.ProcessInstanceRepository;
import de.nemeses.spring.bpmn.repository.ProcessStep;

/**
 * Sets up test data through the real engine: registers process definitions, starts instances and waits for the
 * asynchronous steps.
 */
@Component
class TestProcesses {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private final ProcessDefinitionService definitions;
    private final ProcessEngineService engine;
    private final ProcessInstanceRepository instances;

    TestProcesses(final ProcessDefinitionService definitions, final ProcessEngineService engine,
            final ProcessInstanceRepository instances) {
        this.definitions = definitions;
        this.engine = engine;
        this.instances = instances;
    }

    /**
     * Registers a new version of a process definition.
     *
     * @param name the definition name
     * @param xml the BPMN document
     * @return the name
     */
    String register(final String name, final String xml) {
        this.definitions.register(new Definition(name, null, xml));
        return name;
    }

    /**
     * Registers a process definition under a name no other test uses ({@link TestProcesses.uniqueName}).
     *
     * @param prefix the start of the name
     * @param xml the BPMN document of a process with the given id, e.g. {@link #order}
     * @return the name
     */
    String register(final String prefix, final UnaryOperator<String> xml) {
        final String name = TestProcesses.uniqueName(prefix);
        return this.register(name, xml.apply(name));
    }

    /**
     * Starts an instance of a process that waits in a user task, and waits until the task is open.
     *
     * @param name the definition name
     * @param variables the initial variables
     * @return the instance
     * @throws ProcessEngineException if the instance cannot be started
     * @throws NoSuchProcessInstanceException never, the instance exists
     */
    ProcessInstance waiting(final String name, final Map<String, Object> variables)
            throws ProcessEngineException, NoSuchProcessInstanceException {
        final UUID id = this.engine.startProcess(name, variables).id();
        this.userTasks(id, 1);
        return this.instances.retrieve(id);
    }

    /**
     * Starts an instance and waits until it has ended.
     *
     * @param name the definition name
     * @param variables the initial variables
     * @return the ended instance
     * @throws ProcessEngineException if the instance cannot be started
     */
    ProcessInstance finished(final String name, final Map<String, Object> variables) throws ProcessEngineException {
        return this.ended(this.engine.startProcess(name, variables).id());
    }

    /**
     * Waits until an instance has ended.
     *
     * @param id the instance id
     * @return the ended instance
     */
    ProcessInstance ended(final UUID id) {
        return Awaitility.await().atMost(TIMEOUT).until(() -> this.instances.retrieve(id),
                instance -> instance.endDate() != null);
    }

    /**
     * Waits until an instance has a number of open user tasks.
     *
     * @param id the instance id
     * @param count the number of open user tasks
     * @return the open user tasks as a {@link List}, oldest first
     */
    List<ProcessStep> userTasks(final UUID id, final int count) {
        return Awaitility.await().atMost(TIMEOUT).until(() -> this.engine.openUserTasks(id).stream()
                .sorted(Comparator.comparing(ProcessStep::startDate)).toList(), open -> open.size() == count);
    }

    /**
     * @param prefix the start of the name
     * @return a process definition name no other test uses
     */
    static String uniqueName(final String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    /**
     * @param name the process id
     * @param elements the flow elements of the process
     * @return a BPMN document with one process
     */
    static String document(final String name, final String elements) {
        return "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" id=\"d\" targetNamespace=\"urn:t\">"
                + "<process id=\"" + name + "\">" + elements + "</process></definitions>";
    }

    /**
     * @param name the process id
     * @return a process with start event {@code start}, user task {@code approve} ("Approve order") and end event
     *         {@code end}
     */
    static String order(final String name) {
        return TestProcesses.document(name, "<startEvent id=\"start\"/>"
                + "<userTask id=\"approve\" name=\"Approve order\"/><endEvent id=\"end\"/>"
                + "<sequenceFlow id=\"f1\" sourceRef=\"start\" targetRef=\"approve\"/>"
                + "<sequenceFlow id=\"f2\" sourceRef=\"approve\" targetRef=\"end\"/>");
    }

    /**
     * @param name the process id
     * @return a process that ends right away: start event {@code start}, end event {@code end}
     */
    static String straight(final String name) {
        return TestProcesses.document(name, "<startEvent id=\"start\"/><endEvent id=\"end\"/>"
                + "<sequenceFlow id=\"f1\" sourceRef=\"start\" targetRef=\"end\"/>");
    }

    /**
     * Waits until the clock has passed a date, so that what happens next gets a later date.
     *
     * @param date the date
     */
    static void after(final Date date) {
        Awaitility.await().atMost(TIMEOUT).pollInterval(Duration.ofMillis(1))
                .until(() -> System.currentTimeMillis() > date.getTime());
    }

    /**
     * A process definition version to register; the repository sets the creation date.
     *
     * @param name the name
     * @param createdDate the creation date, {@code null}
     * @param bpmnXml the BPMN XML
     */
    private record Definition(String name, Date createdDate, String bpmnXml) implements ProcessDefinition {
    }
}
