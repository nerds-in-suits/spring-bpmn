package de.nemeses.spring.bpmn.rest;

import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import de.nemeses.spring.bpmn.exec.ProcessEngineException;
import de.nemeses.spring.bpmn.exec.ProcessEngineService;
import de.nemeses.spring.bpmn.repository.NoSuchProcessInstanceException;
import de.nemeses.spring.bpmn.repository.ProcessInstance;
import de.nemeses.spring.bpmn.repository.ProcessInstancePage;
import de.nemeses.spring.bpmn.repository.ProcessInstanceRepository;
import de.nemeses.spring.bpmn.repository.ProcessInstanceSort;
import de.nemeses.spring.bpmn.repository.ProcessStep;
import de.nemeses.spring.bpmn.repository.ProcessStepRepository;

/**
 * Process instances and their steps:
 * <ul>
 * <li>{@code GET /process-instances?definition=&state=running|finished&sort=startDate|endDate&direction=asc|desc&page=&size=}:
 * one page of the instances, optionally of one definition (all versions) and state; sorted by start date
 * (default) or end date (running instances last in ascending, first in descending order), ascending by default;
 * {@code page} is 0-based, {@code size} defaults to {@code spring.bpmn.rest.default-page-size} and is reduced to
 * {@code spring.bpmn.rest.max-page-size};</li>
 * <li>{@code GET /process-instances/{id}}: one instance with its variables;</li>
 * <li>{@code POST /process-instances}: starts the latest version of a definition (body {@link StartProcessRequest});
 * answers {@code 201 Created} with the instance and its {@code Location};</li>
 * <li>{@code GET /process-instances/{id}/steps?state=running|finished&type=&sort=startDate|endDate|type&direction=}:
 * the steps of an instance, optionally of one state and node type (the BPMN element, e.g. {@code userTask}), filtered
 * and sorted in memory by start date (default), end date (running steps last in ascending order) or type, ties by
 * start date.</li>
 * </ul>
 * Instances are neither deleted nor cancelled (#118); their variables are changed through
 * {@link ProcessVariableController}.
 */
@RestController
@RequestMapping("/process-instances")
class ProcessInstanceController {

    private static final String START_DATE = "startDate";
    private static final String END_DATE = "endDate";

    private final ProcessEngineService engine;
    private final ProcessInstanceRepository instances;
    private final ProcessStepRepository steps;
    private final ProcessNodes nodes;
    private final BpmnRestProperties properties;

    ProcessInstanceController(
            final ProcessEngineService engine,
            final ProcessInstanceRepository instances,
            final ProcessStepRepository steps,
            final ProcessNodes nodes,
            final BpmnRestProperties properties) {
        this.engine = engine;
        this.instances = instances;
        this.steps = steps;
        this.nodes = nodes;
        this.properties = properties;
    }

    @GetMapping
    PageDto<ProcessInstanceSummaryDto> list(
            @RequestParam(required = false) final String definition,
            @RequestParam(required = false) final String state,
            @RequestParam(required = false) final String sort,
            @RequestParam(required = false) final String direction,
            @RequestParam(defaultValue = "0") final int page,
            @RequestParam(required = false) final Integer size) {
        final State filter = State.parse(state);
        final ProcessInstanceSort order = ProcessInstanceController.instanceSort(sort);
        final int pageSize = this.pageSize(page, size);
        final ProcessInstancePage result = this.instances.query(
                definition,
                filter == null ? null : filter == State.RUNNING,
                order,
                State.ascending(direction),
                Math.multiplyExact(page, pageSize),
                pageSize);
        return new PageDto<>(
                result.items().stream().map(ProcessInstanceSummaryDto::from).toList(),
                page,
                pageSize,
                result.total());
    }

    @GetMapping("/{id}")
    ProcessInstanceDto get(@PathVariable final UUID id) throws NoSuchProcessInstanceException {
        return ProcessInstanceDto.from(this.instances.retrieve(id));
    }

    @PostMapping
    ResponseEntity<ProcessInstanceDto> start(@RequestBody final StartProcessRequest request)
            throws ProcessEngineException {
        if (request.processDefinition() == null || request.processDefinition().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "processDefinition is required.");
        }
        final ProcessInstance instance = this.engine.startProcess(request.processDefinition(),
                request.variables());
        final URI location = ServletUriComponentsBuilder.fromCurrentRequestUri().path("/{id}")
                .buildAndExpand(instance.id()).toUri();
        return ResponseEntity.created(location).body(ProcessInstanceDto.from(instance));
    }

    /**
     * @return the matching steps as a {@link List}, because they are in the order requested by {@code sort} and
     *         {@code direction}
     */
    @GetMapping("/{id}/steps")
    List<ProcessStepDto> listSteps(
            @PathVariable final UUID id,
            @RequestParam(required = false) final String state,
            @RequestParam(required = false) final String type,
            @RequestParam(required = false) final String sort,
            @RequestParam(required = false) final String direction)
            throws NoSuchProcessInstanceException, ProcessEngineException {
        final State filter = State.parse(state);
        final Comparator<ProcessStepDto> order = ProcessInstanceController.stepOrder(sort);
        final boolean ascending = State.ascending(direction);
        final ProcessInstance instance = this.instances.retrieve(id);
        final Map<String, ProcessNodes.Node> model = this.nodes.nodes(instance.processDefinition());
        final List<ProcessStep> all = this.steps.steps(id);
        return all.stream()
                .map(step -> ProcessStepDto.from(step, model.get(step.nodeId())))
                .filter(step -> filter == null || filter.value().equals(step.state()))
                .filter(step -> type == null || type.equals(step.type()))
                .sorted(ascending ? order : order.reversed())
                .toList();
    }

    private int pageSize(final int page, final Integer size) {
        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must not be negative: " + page);
        }
        if (size == null) {
            return this.properties.defaultPageSize();
        }
        if (size < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "size must be positive: " + size);
        }
        return Math.min(size, this.properties.maxPageSize());
    }

    private static ProcessInstanceSort instanceSort(final String sort) {
        if (sort == null || START_DATE.equals(sort)) {
            return ProcessInstanceSort.START_DATE;
        }
        if (END_DATE.equals(sort)) {
            return ProcessInstanceSort.END_DATE;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Unknown sort '" + sort + "', expected 'startDate' or 'endDate'.");
    }

    private static Comparator<ProcessStepDto> stepOrder(final String sort) {
        final Comparator<ProcessStepDto> byStart = Comparator.comparing(ProcessStepDto::startDate);
        if (sort == null || START_DATE.equals(sort)) {
            return byStart;
        }
        if (END_DATE.equals(sort)) {
            return Comparator.comparing(ProcessStepDto::endDate, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(byStart);
        }
        if ("type".equals(sort)) {
            return Comparator.comparing(ProcessStepDto::type, Comparator.nullsLast(Comparator.<String>naturalOrder()))
                    .thenComparing(byStart);
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Unknown sort '" + sort + "', expected 'startDate', 'endDate' or 'type'.");
    }
}
