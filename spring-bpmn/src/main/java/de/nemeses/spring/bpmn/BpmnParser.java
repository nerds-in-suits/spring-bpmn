package de.nemeses.spring.bpmn;

import java.io.InputStream;
import java.net.URL;

import javax.xml.XMLConstants;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import de.nemeses.spring.bpmn.model.bpmn20.Definitions;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;

/**
 * Unmarshals BPMN 2.0 XML documents into the generated object model. Documents are validated against the BPMN 2.0
 * XML Schema ({@code xsd/bpmn/BPMN20.xsd} on the classpath), which is loaded once per parser. Extension attributes
 * and elements in other namespaces (such as {@code spring:bean}) are allowed by the schema.
 */
@Component
public class BpmnParser {

    private static final String CONTEXT_PATH = "de.nemeses.spring.bpmn.model.bpmn20"
            + ":de.nemeses.spring.bpmn.model.bpmndi:de.nemeses.spring.bpmn.model.dc"
            + ":de.nemeses.spring.bpmn.model.di";

    private static final String SCHEMA_LOCATION = "/xsd/bpmn/BPMN20.xsd";

    private final JAXBContext context;
    private final Schema schema;

    public BpmnParser() throws BpmnParseException {
        try {
            this.context = JAXBContext.newInstance(CONTEXT_PATH, BpmnParser.class.getClassLoader());
        } catch (final JAXBException e) {
            throw new BpmnParseException("Cannot create JAXB context", e);
        }
        this.schema = BpmnParser.loadSchema();
    }

    private static Schema loadSchema() throws BpmnParseException {
        final URL location = BpmnParser.class.getResource(SCHEMA_LOCATION);
        if (location == null) {
            throw new BpmnParseException("BPMN schema " + SCHEMA_LOCATION + " not found on classpath", null);
        }
        try {
            final SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "file,jar,jrt");
            return factory.newSchema(location);
        } catch (final SAXException e) {
            throw new BpmnParseException("Cannot load BPMN schema " + location, e);
        }
    }

    /**
     * @param xml the BPMN document; not closed by this method
     * @return the root {@code definitions} element
     * @throws BpmnParseException if the document is not well-formed XML, does not conform to the BPMN 2.0 schema or
     *             its root is not a {@code definitions} element
     */
    public Definitions parse(final InputStream xml) throws BpmnParseException {
        try {
            final Unmarshaller unmarshaller = this.context.createUnmarshaller();
            unmarshaller.setSchema(this.schema);
            final Object result = unmarshaller.unmarshal(xml);
            if (result instanceof JAXBElement<?> element && element.getValue() instanceof Definitions definitions) {
                return definitions;
            }
            throw new BpmnParseException("Document root is not a BPMN definitions element", null);
        } catch (final JAXBException e) {
            throw new BpmnParseException(BpmnParser.message(e), e);
        }
    }

    private static String message(final JAXBException e) {
        final Throwable cause = e.getLinkedException() == null ? e.getCause() : e.getLinkedException();
        if (cause instanceof final SAXParseException parseException) {
            return "Invalid BPMN document at line " + parseException.getLineNumber() + ", column "
                    + parseException.getColumnNumber() + ": " + parseException.getMessage();
        }
        final String detail = cause == null ? e.getMessage() : cause.getMessage();
        return detail == null ? "Cannot parse BPMN document" : "Cannot parse BPMN document: " + detail;
    }
}
