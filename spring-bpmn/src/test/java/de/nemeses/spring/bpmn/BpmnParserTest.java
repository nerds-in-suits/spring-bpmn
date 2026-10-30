package de.nemeses.spring.bpmn;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.nemeses.spring.bpmn.model.bpmn20.Definitions;
import de.nemeses.spring.bpmn.model.bpmn20.Process;

class BpmnParserTest {

    private static final String DEFINITIONS =
            "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" id=\"d\" targetNamespace=\"urn:t\"";

    private BpmnParser parser;

    @BeforeEach
    void setUp() throws BpmnParseException {
        this.parser = new BpmnParser();
    }

    private Definitions parse(final String resource) throws Exception {
        try (InputStream in = BpmnParserTest.class.getResourceAsStream("/bpmn/" + resource)) {
            Assertions.assertNotNull(in, resource);
            return this.parser.parse(in);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"simple.bpmn", "service-task-gateway.bpmn", "collaboration-diagram.bpmn"})
    void parsesExamples(final String resource) throws Exception {
        final Definitions definitions = parse(resource);
        Assertions.assertNotNull(definitions.getId());
        Assertions.assertEquals("urn:test", definitions.getTargetNamespace());
        Assertions.assertFalse(definitions.getRootElement().isEmpty());
    }

    @Test
    void parsesFlowElementsOfSimpleProcess() throws Exception {
        final Definitions definitions = parse("simple.bpmn");
        final Process process = (Process) definitions.getRootElement().get(0).getValue();
        Assertions.assertEquals("p1", process.getId());
        Assertions.assertEquals(3, process.getFlowElement().size());
    }

    @Test
    void parsesPrefixedProcessWithGateway() throws Exception {
        final Definitions definitions = parse("service-task-gateway.bpmn");
        final Process process = (Process) definitions.getRootElement().get(0).getValue();
        Assertions.assertEquals("Order", process.getName());
        Assertions.assertEquals(9, process.getFlowElement().size());
    }

    @Test
    void parsesCollaborationAndDiagram() throws Exception {
        final Definitions definitions = parse("collaboration-diagram.bpmn");
        Assertions.assertEquals(2, definitions.getRootElement().size());
        Assertions.assertEquals(1, definitions.getBPMNDiagram().size());
    }

    @Test
    void rejectsNonBpmnDocument() {
        final InputStream in = new ByteArrayInputStream("<foo/>".getBytes(StandardCharsets.UTF_8));
        Assertions.assertThrows(BpmnParseException.class, () -> this.parser.parse(in));
    }

    @Test
    void acceptsExtensionAttributesInOtherNamespace() throws Exception {
        final Definitions definitions = this.parseString(DEFINITIONS + " xmlns:spring=\"" + BpmnNodeExecutor.NAMESPACE
                + "\"><process id=\"p\"><serviceTask id=\"s\" spring:bean=\"myBean\"/></process></definitions>");
        Assertions.assertEquals(1, definitions.getRootElement().size());
    }

    @Test
    void rejectsUnknownElement() {
        final BpmnParseException e = Assertions.assertThrows(BpmnParseException.class, () -> this.parseString(
                DEFINITIONS + "><process id=\"p\"><noSuchTask id=\"x\"/></process></definitions>"));
        Assertions.assertTrue(e.getMessage().startsWith("Invalid BPMN document at line 1"), e.getMessage());
        Assertions.assertTrue(e.getMessage().contains("noSuchTask"), e.getMessage());
    }

    @Test
    void rejectsMissingRequiredAttribute() {
        final BpmnParseException e = Assertions.assertThrows(BpmnParseException.class, () -> this.parseString(
                DEFINITIONS + "><process id=\"p\"><sequenceFlow id=\"f\" sourceRef=\"a\"/></process></definitions>"));
        Assertions.assertTrue(e.getMessage().contains("targetRef"), e.getMessage());
    }

    @Test
    void rejectsDuplicateIds() {
        Assertions.assertThrows(BpmnParseException.class, () -> this.parseString(
                DEFINITIONS + "><process id=\"p\"><task id=\"t\"/><task id=\"t\"/></process></definitions>"));
    }

    @Test
    void rejectsMalformedXml() {
        final BpmnParseException e = Assertions.assertThrows(BpmnParseException.class,
                () -> this.parseString(DEFINITIONS + "><process id=\"p\">"));
        Assertions.assertTrue(e.getMessage().startsWith("Invalid BPMN document"), e.getMessage());
    }

    private Definitions parseString(final String xml) throws BpmnParseException {
        return this.parser.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }
}
