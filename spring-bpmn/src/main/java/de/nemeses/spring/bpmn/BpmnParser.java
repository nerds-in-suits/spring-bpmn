package de.nemeses.spring.bpmn;

import java.io.InputStream;

import de.nemeses.spring.bpmn.model.bpmn20.Definitions;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;

/**
 * Unmarshals BPMN 2.0 XML documents into the generated object model.
 */
public class BpmnParser {

    private static final String CONTEXT_PATH = "de.nemeses.spring.bpmn.model.bpmn20"
            + ":de.nemeses.spring.bpmn.model.bpmndi:de.nemeses.spring.bpmn.model.dc"
            + ":de.nemeses.spring.bpmn.model.di";

    private final JAXBContext context;

    public BpmnParser() throws BpmnParseException {
        try {
            this.context = JAXBContext.newInstance(CONTEXT_PATH, BpmnParser.class.getClassLoader());
        } catch (final JAXBException e) {
            throw new BpmnParseException("Cannot create JAXB context", e);
        }
    }

    /**
     * @param xml the BPMN document; not closed by this method
     * @return the root {@code definitions} element
     * @throws BpmnParseException if the document is not valid BPMN XML
     */
    public Definitions parse(final InputStream xml) throws BpmnParseException {
        try {
            final Object result = this.context.createUnmarshaller().unmarshal(xml);
            if (result instanceof JAXBElement<?> element && element.getValue() instanceof Definitions definitions) {
                return definitions;
            }
            throw new BpmnParseException("Document root is not a BPMN definitions element", null);
        } catch (final JAXBException e) {
            throw new BpmnParseException("Cannot parse BPMN document", e);
        }
    }
}
