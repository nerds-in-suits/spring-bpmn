package de.nemeses.spring.bpmn;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import de.nemeses.spring.bpmn.model.bpmn20.Definitions;
import de.nemeses.spring.bpmn.repository.ProcessDefinition;

/**
 * Caches the parsed BPMN document of each process definition, so that {@link ProcessDefinition#bpmnXml()} is parsed
 * once instead of for every started instance and executed node. Process definitions are immutable, so the document is
 * keyed by {@link ProcessDefinition#md5Hash()}: every version with the same XML shares one parsed model. The cached
 * model is shared between threads and must be treated as read-only.
 */
@Component
public class ProcessDefinitionModelCache {

    private final BpmnParser parser;
    private final Map<String, Definitions> documents = new ConcurrentHashMap<>();

    ProcessDefinitionModelCache(final BpmnParser parser) {
        this.parser = parser;
    }

    /**
     * Returns the parsed BPMN document of a definition, parsing it on first use.
     *
     * @param definition the definition
     * @return the {@code definitions} element of the document; must not be modified
     * @throws ProcessEngineException if the document cannot be parsed
     */
    public Definitions document(final ProcessDefinition definition) throws ProcessEngineException {
        final String key = definition.md5Hash();
        final Definitions cached = this.documents.get(key);
        if (cached != null) {
            return cached;
        }
        final Definitions parsed;
        try {
            parsed = this.parser.parse(new ByteArrayInputStream(definition.bpmnXml().getBytes(StandardCharsets.UTF_8)));
        } catch (final BpmnParseException e) {
            throw new ProcessEngineException("Cannot parse process definition '" + definition.name() + "'.", e);
        }
        final Definitions previous = this.documents.putIfAbsent(key, parsed);
        return previous == null ? parsed : previous;
    }
}
