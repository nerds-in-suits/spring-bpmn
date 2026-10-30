package de.nemeses.spring.bpmn;

import java.io.ByteArrayInputStream;
import java.lang.ref.SoftReference;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import de.nemeses.spring.bpmn.model.bpmn20.Definitions;
import de.nemeses.spring.bpmn.repository.ProcessDefinition;

/**
 * Caches the parsed BPMN document of each process definition, so that {@link ProcessDefinition#bpmnXml()} is parsed
 * once instead of for every started instance and executed node.
 * <p>
 * A process definition is identified by its {@link ProcessDefinition#name() name} and
 * {@link ProcessDefinition#createdDate() creation date} (compared in milliseconds), and a registered definition is
 * never changed, so the document is cached under that pair. A definition without creation date (not registered yet)
 * is parsed on every call and not cached. The documents are held by {@link SoftReference}s, so the garbage collector
 * may drop them under memory pressure; a dropped document is parsed again on its next use. Parse errors are not
 * cached. The cached model is shared between threads and must be treated as read-only.
 */
@Component
public class ProcessDefinitionModelCache {

    private final BpmnParser parser;
    private final Map<Key, SoftReference<Definitions>> documents = new ConcurrentHashMap<>();

    ProcessDefinitionModelCache(final BpmnParser parser) {
        this.parser = parser;
    }

    /**
     * Returns the parsed BPMN document of a definition, parsing it on first use or after the garbage collector dropped
     * it.
     *
     * @param definition the definition
     * @return the {@code definitions} element of the document; must not be modified
     * @throws ProcessEngineException if the document cannot be parsed
     */
    public Definitions document(final ProcessDefinition definition) throws ProcessEngineException {
        final Key key = ProcessDefinitionModelCache.key(definition);
        if (key == null) {
            return this.parse(definition);
        }
        final SoftReference<Definitions> reference = this.documents.get(key);
        final Definitions cached = reference == null ? null : reference.get();
        if (cached != null) {
            return cached;
        }
        final Definitions parsed = this.parse(definition);
        this.documents.put(key, new SoftReference<>(parsed));
        return parsed;
    }

    /**
     * Returns the reference holding the cached document of a definition, for tests.
     *
     * @param definition the definition
     * @return the reference, {@code null} if none is cached
     */
    SoftReference<Definitions> reference(final ProcessDefinition definition) {
        final Key key = ProcessDefinitionModelCache.key(definition);
        return key == null ? null : this.documents.get(key);
    }

    private Definitions parse(final ProcessDefinition definition) throws ProcessEngineException {
        try {
            return this.parser.parse(new ByteArrayInputStream(definition.bpmnXml().getBytes(StandardCharsets.UTF_8)));
        } catch (final BpmnParseException e) {
            throw new ProcessEngineException("Cannot parse process definition '" + definition.name() + "'.", e);
        }
    }

    private static Key key(final ProcessDefinition definition) {
        final Date created = definition.createdDate();
        return created == null ? null : new Key(definition.name(), created.getTime());
    }

    /**
     * Identifier of a process definition: its name and creation date in milliseconds (so that a {@link Date} and a
     * {@link java.sql.Timestamp} of the same instant are equal).
     *
     * @param name the definition name
     * @param created the creation date in milliseconds since the epoch
     */
    private record Key(String name, long created) {
    }
}
