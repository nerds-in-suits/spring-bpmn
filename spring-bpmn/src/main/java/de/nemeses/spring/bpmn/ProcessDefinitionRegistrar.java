package de.nemeses.spring.bpmn;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Date;

import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

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
@Component
public class ProcessDefinitionRegistrar implements ApplicationRunner {

    private final BpmnParser parser;
    private final ProcessDefinitionService service;
    private final Collection<ProcessDefinitionSource> sources;

    ProcessDefinitionRegistrar(final BpmnParser parser, final ProcessDefinitionService service,
            final Collection<ProcessDefinitionSource> sources) {
        this.parser = parser;
        this.service = service;
        this.sources = sources;
    }

    @Override
    public void run(final ApplicationArguments args) throws IOException, BpmnParseException {
        registerAll();
    }

    /**
     * Registers all resources of all {@link ProcessDefinitionSource}s.
     *
     * @throws IOException if a resource cannot be read
     * @throws BpmnParseException if a resource is not a valid BPMN document
     */
    public void registerAll() throws IOException, BpmnParseException {
        for (final ProcessDefinitionSource source : this.sources) {
            for (final Resource resource : source.resources()) {
                register(resource);
            }
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
                final String name = processName(process);
                register(new ProcessDefinition() {
                    @Override
                    public String name() {
                        return name;
                    }

                    @Override
                    public Date createdDate() {
                        return null;
                    }

                    @Override
                    public String bpmnXml() {
                        return xml;
                    }
                }, resource);
            }
        }
    }

    private void register(final ProcessDefinition definition, final Resource resource) {
        try {
            if (this.service.retrieveLatest(definition.name()).md5Hash().equals(definition.md5Hash())) {
                LoggerFactory.getLogger(this.getClass()).debug("Process definition {} from {} is unchanged",
                        definition.name(), resource);
                return;
            }
        } catch (final NoSuchProcessDefinitionException e) {
            LoggerFactory.getLogger(this.getClass()).debug("Process definition {} is new", definition.name());
        }
        this.service.register(definition);
        LoggerFactory.getLogger(this.getClass()).info("Registered process definition {} from {}", definition.name(), resource);
    }

    private static String processName(final Process process) {
        final String name = process.getName();
        return name == null || name.isBlank() ? process.getId() : name;
    }
}
