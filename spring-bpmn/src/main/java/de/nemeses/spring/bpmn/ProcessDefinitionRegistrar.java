package de.nemeses.spring.bpmn;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

import de.nemeses.spring.bpmn.model.bpmn20.Definitions;
import de.nemeses.spring.bpmn.model.bpmn20.Process;
import de.nemeses.spring.bpmn.model.bpmn20.RootElement;
import de.nemeses.spring.bpmn.repository.NoSuchProcessDefinitionException;
import de.nemeses.spring.bpmn.repository.ProcessDefinition;
import de.nemeses.spring.bpmn.repository.ProcessDefinitionService;
import jakarta.xml.bind.JAXBElement;

/**
 * Registers BPMN documents as process definitions. Each process of a document is registered separately under its
 * {@code name} (or its {@code id} if it has no name); the whole document is stored as XML. A process is skipped if
 * the latest registered version has the same MD5 hash.
 */
public class ProcessDefinitionRegistrar {

    private static final Logger LOG = LoggerFactory.getLogger(ProcessDefinitionRegistrar.class);

    private final BpmnParser parser;
    private final ProcessDefinitionService service;
    private final ResourcePatternResolver resolver;
    private final List<String> locations;
    private final Collection<ProcessDefinitionSource> sources;

    ProcessDefinitionRegistrar(final BpmnParser parser, final ProcessDefinitionService service,
            final ResourcePatternResolver resolver, final List<String> locations,
            final Collection<ProcessDefinitionSource> sources) {
        this.parser = parser;
        this.service = service;
        this.resolver = resolver;
        this.locations = locations;
        this.sources = sources;
    }

    /**
     * Registers all resources of the configured locations and of all {@link ProcessDefinitionSource}s.
     *
     * @throws IOException if a resource cannot be read
     * @throws BpmnParseException if a resource is not a valid BPMN document
     */
    public void registerAll() throws IOException, BpmnParseException {
        final List<Resource> resources = new ArrayList<>();
        for (final String location : this.locations) {
            resources.addAll(List.of(this.resolver.getResources(location)));
        }
        for (final ProcessDefinitionSource source : this.sources) {
            resources.addAll(source.resources());
        }
        for (final Resource resource : resources) {
            register(resource);
        }
    }

    private void register(final Resource resource) throws IOException, BpmnParseException {
        final byte[] content;
        try (InputStream in = resource.getInputStream()) {
            content = in.readAllBytes();
        }
        final String xml = new String(content, StandardCharsets.UTF_8);
        final Definitions definitions = this.parser.parse(new ByteArrayInputStream(content));
        for (final JAXBElement<? extends RootElement> element : definitions.getRootElement()) {
            if (element.getValue() instanceof Process process) {
                register(new ParsedProcessDefinition(processName(process), null, xml), resource);
            }
        }
    }

    private void register(final ProcessDefinition definition, final Resource resource) {
        try {
            if (this.service.retrieveLatest(definition.name()).md5Hash().equals(definition.md5Hash())) {
                LOG.debug("Process definition {} from {} is unchanged", definition.name(), resource);
                return;
            }
        } catch (final NoSuchProcessDefinitionException e) {
            LOG.debug("Process definition {} is new", definition.name());
        }
        this.service.register(definition);
        LOG.info("Registered process definition {} from {}", definition.name(), resource);
    }

    private static String processName(final Process process) {
        final String name = process.getName();
        return name == null || name.isBlank() ? process.getId() : name;
    }
}
