package de.nemeses.spring.bpmn.rest;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Map;

import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import de.nemeses.spring.bpmn.BpmnParseException;
import de.nemeses.spring.bpmn.BpmnParser;
import de.nemeses.spring.bpmn.ProcessDefinitionRegistrar;
import de.nemeses.spring.bpmn.model.bpmn20.Definitions;
import de.nemeses.spring.bpmn.model.bpmn20.Process;
import de.nemeses.spring.bpmn.model.bpmn20.RootElement;
import de.nemeses.spring.bpmn.repository.NoSuchProcessDefinitionException;
import de.nemeses.spring.bpmn.repository.ProcessDefinition;
import de.nemeses.spring.bpmn.repository.ProcessDefinitionService;
import de.nemeses.spring.bpmn.repository.ProcessInstanceCount;
import de.nemeses.spring.bpmn.repository.ProcessInstanceRepository;
import jakarta.xml.bind.JAXBElement;

/**
 * Process definitions, addressed by name (the latest version, unless stated otherwise):
 * <ul>
 * <li>{@code GET /process-definitions}: the latest version of every definition with the number of its running and
 * finished instances (over all versions), ordered by name;</li>
 * <li>{@code GET /process-definitions/{name}}: the latest version;</li>
 * <li>{@code GET /process-definitions/{name}/versions}: all versions, newest first;</li>
 * <li>{@code GET /process-definitions/{name}/bpmn}: the BPMN XML of the latest version;</li>
 * <li>{@code POST /process-definitions}: deploys a BPMN document (body {@code application/xml}), only if
 * {@code spring.bpmn.rest.deployment-enabled} is {@code true}, else {@code 403 Forbidden}. Like
 * {@link ProcessDefinitionRegistrar}, each {@code process} of the document is registered under its name (or id) with
 * the whole document as XML, unless the latest version has the same MD5 hash.</li>
 * </ul>
 * Definitions are never deleted, because instances and steps refer to them.
 */
@RestController
@RequestMapping("/process-definitions")
class ProcessDefinitionController {

    private final ProcessDefinitionService definitions;
    private final ProcessInstanceRepository instances;
    private final BpmnParser parser;
    private final BpmnRestProperties properties;

    ProcessDefinitionController(final ProcessDefinitionService definitions, final ProcessInstanceRepository instances,
            final BpmnParser parser, final BpmnRestProperties properties) {
        this.definitions = definitions;
        this.instances = instances;
        this.parser = parser;
        this.properties = properties;
    }

    @GetMapping
    Collection<ProcessDefinitionSummaryDto> list() {
        final Map<String, ProcessInstanceCount> counts = this.instances.countByDefinition();
        return this.definitions.retrieveLatestAll().stream()
                .map(definition -> ProcessDefinitionSummaryDto.from(definition, counts.get(definition.name())))
                .toList();
    }

    @GetMapping("/{name}")
    ProcessDefinitionDto latest(@PathVariable final String name) throws NoSuchProcessDefinitionException {
        return ProcessDefinitionDto.from(this.definitions.retrieveLatest(name));
    }

    @GetMapping("/{name}/versions")
    Collection<ProcessDefinitionDto> versions(@PathVariable final String name) throws NoSuchProcessDefinitionException {
        final Collection<ProcessDefinition> versions = this.definitions.retrieveAll(name);
        if (versions.isEmpty()) {
            throw new NoSuchProcessDefinitionException(name);
        }
        return versions.stream().map(ProcessDefinitionDto::from).toList();
    }

    @GetMapping(path = "/{name}/bpmn", produces = MediaType.APPLICATION_XML_VALUE)
    String bpmn(@PathVariable final String name) throws NoSuchProcessDefinitionException {
        return this.definitions.retrieveLatest(name).bpmnXml();
    }

    @PostMapping(consumes = { MediaType.APPLICATION_XML_VALUE, MediaType.TEXT_XML_VALUE })
    Collection<DeployedProcessDefinitionDto> deploy(@RequestBody final byte[] content)
            throws BpmnParseException, NoSuchProcessDefinitionException {
        if (!this.properties.deploymentEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Deployment is disabled (spring.bpmn.rest.deployment-enabled).");
        }
        final String xml = new String(content, StandardCharsets.UTF_8);
        final Definitions document = this.parser.parse(new ByteArrayInputStream(content));
        final Collection<DeployedProcessDefinitionDto> deployed = new ArrayList<>();
        for (final JAXBElement<? extends RootElement> element : document.getRootElement()) {
            if (element.getValue() instanceof final Process process) {
                final String name = ProcessDefinitionRegistrar.processName(process);
                final boolean registered = this.register(name, xml);
                deployed.add(DeployedProcessDefinitionDto.from(this.definitions.retrieveLatest(name), registered));
            }
        }
        if (deployed.isEmpty()) {
            throw new BpmnParseException("The BPMN document contains no process.", null);
        }
        return deployed;
    }

    /**
     * @return {@code true} if a new version was registered, {@code false} if the latest version is unchanged
     */
    private boolean register(final String name, final String xml) {
        final ProcessDefinition definition = new DeployedDefinition(name, xml);
        try {
            if (this.definitions.retrieveLatest(name).md5Hash().equals(definition.md5Hash())) {
                return false;
            }
        } catch (final NoSuchProcessDefinitionException e) {
            LoggerFactory.getLogger(this.getClass()).debug("Process definition {} is new", name);
        }
        this.definitions.register(definition);
        LoggerFactory.getLogger(this.getClass()).info("Deployed process definition {}", name);
        return true;
    }

    /**
     * A deployed process; the repository sets its creation date.
     *
     * @param name the process definition name
     * @param bpmnXml the whole BPMN document
     */
    private record DeployedDefinition(
            String name,
            String bpmnXml) implements ProcessDefinition {

        @Override
        public Date createdDate() {
            return null;
        }
    }
}
