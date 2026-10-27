package de.nemeses.spring.bpmn;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import de.nemeses.spring.bpmn.repository.ProcessDefinition;
import de.nemeses.spring.bpmn.repository.ProcessInstance;
import de.nemeses.spring.bpmn.repository.ProcessInstanceRepository;

/**
 * Keeps created process instances in memory.
 */
class InMemoryProcessInstanceRepository implements ProcessInstanceRepository {

    private final List<ProcessInstance> created = new ArrayList<>();

    @Override
    public ProcessInstance create(final ProcessDefinition definition, final Map<String, Object> variables) {
        final ProcessInstance instance = new Instance(UUID.randomUUID(), definition, Map.copyOf(variables),
                new Date(), null);
        this.created.add(instance);
        return instance;
    }

    List<ProcessInstance> created() {
        return this.created;
    }

    /**
     * A created instance.
     *
     * @param id the id
     * @param processDefinition the definition
     * @param variables the variables
     * @param startDate the start date
     * @param endDate the end date
     */
    record Instance(UUID id, ProcessDefinition processDefinition, Map<String, Object> variables, Date startDate,
            Date endDate) implements ProcessInstance {
    }
}
