package de.nemeses.spring.bpmn.rest;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import de.nemeses.spring.bpmn.exec.ProcessEngineException;
import de.nemeses.spring.bpmn.exec.ProcessEngineService;
import de.nemeses.spring.bpmn.repository.NoSuchProcessInstanceException;
import de.nemeses.spring.bpmn.repository.ProcessInstanceRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The variables of a process instance ({@link ProcessEngineService#updateVariables}):
 * <ul>
 * <li>{@code GET /process-instances/{id}/variables}: all variables as a JSON object (the same as in
 * {@code GET /process-instances/{id}});</li>
 * <li>{@code PATCH /process-instances/{id}/variables}: merges the JSON object body ({@code application/json} or
 * {@code application/merge-patch+json}) into the variables: each member adds or replaces the variable of its name and
 * a {@code null} member removes it (JSON Merge Patch, RFC 7396, at the top level only; an object value replaces the
 * old value as a whole); answers {@code 200 OK} with all resulting variables;</li>
 * <li>{@code PUT /process-instances/{id}/variables}: replaces all variables with the JSON object body, whose members
 * must not be {@code null}; answers {@code 200 OK} with all resulting variables;</li>
 * <li>{@code GET /process-instances/{id}/variables/{name}}: the JSON value of one variable; {@code 404} if the instance
 * has no variable of that name;</li>
 * <li>{@code PUT /process-instances/{id}/variables/{name}}: sets one variable to the JSON value of the body, which must
 * not be {@code null}; answers {@code 200 OK} with the stored value;</li>
 * <li>{@code DELETE /process-instances/{id}/variables/{name}}: removes one variable; answers {@code 204 No Content},
 * also if the instance had no variable of that name.</li>
 * </ul>
 * An unknown instance answers {@code 404}; changing the variables of an ended instance, or a {@code null} member in
 * {@code PUT}, answers {@code 422}; a missing body or a body that is not a JSON object (for the whole set) answers
 * {@code 400}.
 * <p>
 * The changes run in one transaction but do not lock the instance, so they may interleave with a step executing at the
 * same time, which may overwrite them with the variables it read before (see
 * {@link ProcessEngineService#updateVariables}).
 */
@RestController
@RequestMapping("/process-instances/{id}/variables")
class ProcessVariableController {

    private final ProcessEngineService engine;
    private final ProcessInstanceRepository instances;
    private final JsonMapper json;

    ProcessVariableController(
            final ProcessEngineService engine,
            final ProcessInstanceRepository instances,
            final JsonMapper json) {
        this.engine = engine;
        this.instances = instances;
        this.json = json;
    }

    @GetMapping
    Map<String, Object> list(@PathVariable final UUID id) throws NoSuchProcessInstanceException {
        return this.instances.retrieve(id).variables();
    }

    @PatchMapping
    Map<String, Object> merge(@PathVariable final UUID id, @RequestBody final Map<String, Object> variables)
            throws ProcessEngineException {
        return this.engine.updateVariables(id, variables, false).variables();
    }

    @PutMapping
    Map<String, Object> replace(@PathVariable final UUID id, @RequestBody final Map<String, Object> variables)
            throws ProcessEngineException {
        return this.engine.updateVariables(id, variables, true).variables();
    }

    /**
     * @return the value as a {@link JsonNode}, so that a string value is written as a JSON string and not as plain
     *         text
     */
    @GetMapping("/{name}")
    JsonNode get(@PathVariable final UUID id, @PathVariable final String name) throws NoSuchProcessInstanceException {
        final Object value = this.instances.retrieve(id).variables().get(name);
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Process instance " + id + " has no variable '" + name + "'.");
        }
        return this.json.valueToTree(value);
    }

    /**
     * @return the stored value as a {@link JsonNode}, so that a string value is written as a JSON string
     */
    @PutMapping("/{name}")
    JsonNode set(@PathVariable final UUID id, @PathVariable final String name, @RequestBody final Object value)
            throws ProcessEngineException {
        return this.json.valueToTree(this.engine.updateVariables(id, Map.of(name, value), false).variables().get(name));
    }

    @DeleteMapping("/{name}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable final UUID id, @PathVariable final String name) throws ProcessEngineException {
        final Map<String, Object> removal = new HashMap<>();
        removal.put(name, null);
        this.engine.updateVariables(id, removal, false);
    }
}
