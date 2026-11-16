package de.nemeses.spring.bpmn.rest;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import de.nemeses.spring.bpmn.exec.ProcessEngineException;
import de.nemeses.spring.bpmn.exec.ProcessEngineService;
import de.nemeses.spring.bpmn.repository.NoSuchProcessInstanceException;
import de.nemeses.spring.bpmn.repository.ProcessInstanceRepository;
import de.nemeses.spring.bpmn.repository.ProcessStep;

/**
 * Open user tasks ({@link ProcessEngineService#openUserTasks}, {@link ProcessEngineService#completeUserTask}):
 * <ul>
 * <li>{@code GET /user-tasks?processInstance=}: the open user tasks of a process instance, oldest first;</li>
 * <li>{@code POST /user-tasks/{id}/complete}: completes an open user task (the id of its step) with the variables of
 * the optional JSON object body, which are added to or replace process variables; answers {@code 204 No Content}.
 * A task that is already completed or not a user task answers {@code 422}.</li>
 * </ul>
 */
@RestController
@RequestMapping("/user-tasks")
class UserTaskController {

    private final ProcessEngineService engine;
    private final ProcessInstanceRepository instances;
    private final ProcessNodes nodes;

    UserTaskController(final ProcessEngineService engine, final ProcessInstanceRepository instances,
            final ProcessNodes nodes) {
        this.engine = engine;
        this.instances = instances;
        this.nodes = nodes;
    }

    /**
     * @return the open user tasks as a {@link List}, because they are ordered by start date, oldest first
     */
    @GetMapping
    List<UserTaskDto> list(@RequestParam final UUID processInstance)
            throws ProcessEngineException, NoSuchProcessInstanceException {
        final Collection<ProcessStep> open = this.engine.openUserTasks(processInstance);
        if (open.isEmpty()) {
            return List.of();
        }
        final Map<String, ProcessNodes.Node> model = this.nodes.nodes(
                this.instances.retrieve(processInstance).processDefinition());
        return open.stream()
                .map(step -> UserTaskDto.from(step, model.get(step.nodeId())))
                .sorted(Comparator.comparing(UserTaskDto::startDate))
                .toList();
    }

    @PostMapping("/{id}/complete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void complete(@PathVariable final UUID id, @RequestBody(required = false) final Map<String, Object> variables)
            throws ProcessEngineException {
        this.engine.completeUserTask(id, variables);
    }
}
