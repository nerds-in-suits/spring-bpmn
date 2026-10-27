package de.nemeses.spring.bpmn;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import de.nemeses.spring.bpmn.repository.NoSuchProcessInstanceException;
import de.nemeses.spring.bpmn.repository.ProcessDefinition;
import de.nemeses.spring.bpmn.repository.ProcessInstance;
import de.nemeses.spring.bpmn.repository.ProcessInstanceRepository;

/**
 * Keeps created process instances in memory, in creation order.
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

    @Override
    public ProcessInstance retrieve(final UUID id) throws NoSuchProcessInstanceException {
        return this.created.get(this.index(id));
    }

    @Override
    public ProcessInstance lock(final UUID id) throws NoSuchProcessInstanceException {
        return this.retrieve(id);
    }

    @Override
    public ProcessInstance updateEndDate(final UUID id, final Date endDate) throws NoSuchProcessInstanceException {
        final int index = this.index(id);
        final ProcessInstance old = this.created.get(index);
        final ProcessInstance updated = new Instance(id, old.processDefinition(), old.variables(), old.startDate(),
                endDate);
        this.created.set(index, updated);
        return updated;
    }

    @Override
    public ProcessInstance updateVariables(final UUID id, final Map<String, Object> variables)
            throws NoSuchProcessInstanceException {
        final int index = this.index(id);
        final ProcessInstance old = this.created.get(index);
        final ProcessInstance updated = new Instance(id, old.processDefinition(), Map.copyOf(variables),
                old.startDate(), old.endDate());
        this.created.set(index, updated);
        return updated;
    }

    private int index(final UUID id) throws NoSuchProcessInstanceException {
        for (int i = 0; i < this.created.size(); i++) {
            if (this.created.get(i).id().equals(id)) {
                return i;
            }
        }
        throw new NoSuchProcessInstanceException(id);
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
