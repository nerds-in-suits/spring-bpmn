package de.nemeses.spring.bpmn;

import java.io.InputStream;
import java.sql.Timestamp;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.nemeses.spring.bpmn.model.bpmn20.Definitions;
import de.nemeses.spring.bpmn.repository.ProcessDefinition;

class ProcessDefinitionModelCacheTest {

    private static final Date CREATED = new Date(1_000_000L);

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

    private static ProcessDefinition definition(final String name, final Date created, final String bpmnXml) {
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
        final ProcessDefinition definition = ProcessDefinitionModelCacheTest.definition("a",
                ProcessDefinitionModelCacheTest.CREATED, ProcessDefinitionModelCacheTest.bpmn("a"));

        final Definitions first = this.cache.document(definition);
        final Definitions second = this.cache.document(definition);

        Assertions.assertSame(first, second);
        Assertions.assertEquals(1, this.parses.get());
    }

    @Test
    void identifiesDefinitionsByNameAndCreationDate() throws ProcessEngineException {
        final Definitions first = this.cache.document(ProcessDefinitionModelCacheTest.definition("a",
                new Date(ProcessDefinitionModelCacheTest.CREATED.getTime()), ProcessDefinitionModelCacheTest.bpmn("a")));
        final Definitions second = this.cache.document(ProcessDefinitionModelCacheTest.definition("a",
                new Timestamp(ProcessDefinitionModelCacheTest.CREATED.getTime()), ProcessDefinitionModelCacheTest.bpmn("a")));

        Assertions.assertSame(first, second);
        Assertions.assertEquals(1, this.parses.get());
    }

    @Test
    void parsesOtherVersionOfSameNameSeparately() throws ProcessEngineException {
        final String xml = ProcessDefinitionModelCacheTest.bpmn("a");

        final Definitions first = this.cache.document(
                ProcessDefinitionModelCacheTest.definition("a", ProcessDefinitionModelCacheTest.CREATED, xml));
        final Definitions second = this.cache.document(
                ProcessDefinitionModelCacheTest.definition("a", new Date(2_000_000L), xml));

        Assertions.assertNotSame(first, second);
        Assertions.assertEquals(2, this.parses.get());
    }

    @Test
    void parsesOtherNameWithSameCreationDateSeparately() throws ProcessEngineException {
        final Definitions first = this.cache.document(ProcessDefinitionModelCacheTest.definition("a",
                ProcessDefinitionModelCacheTest.CREATED, ProcessDefinitionModelCacheTest.bpmn("a")));
        final Definitions second = this.cache.document(ProcessDefinitionModelCacheTest.definition("b",
                ProcessDefinitionModelCacheTest.CREATED, ProcessDefinitionModelCacheTest.bpmn("b")));

        Assertions.assertNotSame(first, second);
        Assertions.assertEquals(2, this.parses.get());
    }

    @Test
    void parsesDefinitionWithoutCreationDateWithoutCachingIt() throws ProcessEngineException {
        final ProcessDefinition definition = ProcessDefinitionModelCacheTest.definition("a", null,
                ProcessDefinitionModelCacheTest.bpmn("a"));

        this.cache.document(definition);
        this.cache.document(definition);

        Assertions.assertNull(this.cache.reference(definition));
        Assertions.assertEquals(2, this.parses.get());
    }

    @Test
    void holdsDocumentsSoftlyAndParsesClearedOnesAgain() throws ProcessEngineException {
        final ProcessDefinition definition = ProcessDefinitionModelCacheTest.definition("a",
                ProcessDefinitionModelCacheTest.CREATED, ProcessDefinitionModelCacheTest.bpmn("a"));
        final Definitions first = this.cache.document(definition);

        this.cache.reference(definition).clear();
        final Definitions second = this.cache.document(definition);

        Assertions.assertNotSame(first, second);
        Assertions.assertSame(second, this.cache.reference(definition).get());
        Assertions.assertEquals(2, this.parses.get());
    }

    @Test
    void failsOnInvalidDocumentWithoutCachingIt() {
        final ProcessDefinition definition = ProcessDefinitionModelCacheTest.definition("a",
                ProcessDefinitionModelCacheTest.CREATED, "<notBpmn/>");

        Assertions.assertThrows(ProcessEngineException.class, () -> this.cache.document(definition));
        Assertions.assertThrows(ProcessEngineException.class, () -> this.cache.document(definition));
        Assertions.assertNull(this.cache.reference(definition));
        Assertions.assertEquals(2, this.parses.get());
    }
}
