package de.nemeses.spring.bpmn;

import java.io.InputStream;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.nemeses.spring.bpmn.model.bpmn20.Definitions;
import de.nemeses.spring.bpmn.repository.ProcessDefinition;

class ProcessDefinitionModelCacheTest {

    private final AtomicInteger parses = new AtomicInteger();
    private ProcessDefinitionModelCache cache;

    @BeforeEach
    void setUp() throws BpmnParseException {
        this.cache = new ProcessDefinitionModelCache(new BpmnParser() {
            @Override
            public Definitions parse(final InputStream xml) throws BpmnParseException {
                ProcessDefinitionModelCacheTest.this.parses.incrementAndGet();
                return super.parse(xml);
            }
        });
    }

    private static ProcessDefinition definition(final String name, final String bpmnXml) {
        final Date created = new Date();
        return new ProcessDefinition() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public Date createdDate() {
                return created;
            }

            @Override
            public String bpmnXml() {
                return bpmnXml;
            }
        };
    }

    private static String bpmn(final String processId) {
        return "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" id=\"d\" targetNamespace=\"urn:t\">"
                + "<process id=\"" + processId + "\"><startEvent id=\"s\"/></process></definitions>";
    }

    @Test
    void parsesEachDocumentOnce() throws ProcessEngineException {
        final ProcessDefinition definition = ProcessDefinitionModelCacheTest.definition("a", ProcessDefinitionModelCacheTest.bpmn("a"));

        final Definitions first = this.cache.document(definition);
        final Definitions second = this.cache.document(definition);

        Assertions.assertSame(first, second);
        Assertions.assertEquals(1, this.parses.get());
    }

    @Test
    void sharesDocumentBetweenDefinitionsWithSameXml() throws ProcessEngineException {
        final String xml = ProcessDefinitionModelCacheTest.bpmn("a");

        final Definitions first = this.cache.document(ProcessDefinitionModelCacheTest.definition("a", xml));
        final Definitions second = this.cache.document(ProcessDefinitionModelCacheTest.definition("a", xml));

        Assertions.assertSame(first, second);
        Assertions.assertEquals(1, this.parses.get());
    }

    @Test
    void parsesDifferentDocumentsSeparately() throws ProcessEngineException {
        final Definitions first = this.cache.document(
                ProcessDefinitionModelCacheTest.definition("a", ProcessDefinitionModelCacheTest.bpmn("a")));
        final Definitions second = this.cache.document(
                ProcessDefinitionModelCacheTest.definition("b", ProcessDefinitionModelCacheTest.bpmn("b")));

        Assertions.assertNotSame(first, second);
        Assertions.assertEquals(2, this.parses.get());
    }

    @Test
    void failsOnInvalidDocumentWithoutCachingIt() {
        final ProcessDefinition definition = ProcessDefinitionModelCacheTest.definition("a", "<notBpmn/>");

        Assertions.assertThrows(ProcessEngineException.class, () -> this.cache.document(definition));
        Assertions.assertThrows(ProcessEngineException.class, () -> this.cache.document(definition));
        Assertions.assertEquals(2, this.parses.get());
    }
}
