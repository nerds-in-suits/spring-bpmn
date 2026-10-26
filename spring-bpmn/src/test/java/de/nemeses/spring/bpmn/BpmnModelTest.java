package de.nemeses.spring.bpmn;

import java.io.StringReader;

import javax.xml.transform.stream.StreamSource;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import de.nemeses.spring.bpmn.model.bpmn20.Definitions;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;

/**
 *
 */
class BpmnModelTest {

    private static final String XML = "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\""
            + " id=\"d1\" targetNamespace=\"urn:test\"><process id=\"p1\"><startEvent id=\"s1\"/></process>"
            + "</definitions>";

    @Test
    void unmarshalsDefinitions() throws Exception {
        final JAXBContext context = JAXBContext.newInstance(Definitions.class.getPackageName());
        final JAXBElement<Definitions> element = context.createUnmarshaller()
                .unmarshal(new StreamSource(new StringReader(XML)), Definitions.class);
        Assertions.assertEquals("d1", element.getValue().getId());
        Assertions.assertEquals(1, element.getValue().getRootElement().size());
    }
}
