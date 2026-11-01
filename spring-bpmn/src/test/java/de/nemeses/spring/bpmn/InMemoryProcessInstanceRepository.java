package de.nemeses.spring.bpmn;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import de.nemeses.spring.bpmn.repository.NoSuchProcessInstanceException;
import de.nemeses.spring.bpmn.repository.ProcessDefinition;
import de.nemeses.spring.bpmn.repository.ProcessInstance;
import de.nemeses.spring.bpmn.repository.ProcessInstanceRepository;
import de.nemeses.spring.bpmn.repository.ProcessStep;

/**
 * Keeps created process instances in memory, in creation order.
 */
public class InMemoryProcessInstanceRepository implements ProcessInstanceRepository {

    private final List<ProcessInstance> created = new ArrayList<>();

    @Override
    public ProcessInstance create(final ProcessDefinition definition, final Map<String, Object> variables,
            final ProcessStep parentStep) {
        final ProcessInstance parentInstance = parentStep == null ? null
                : this.created.stream().filter(i -> i.id().equals(parentStep.processInstanceId())).findFirst()
                        .orElseThrow();
        final ProcessInstance instance = new Instance(UUID.randomUUID(), definition, Map.copyOf(variables),
                new Date(), null, parentStep, parentInstance);
        this.created.add(instance);
        return instance;
    }

    @Override
    public Optional<ProcessInstance> retrieveChild(final UUID parentStepId) {
        return this.created.stream()
                .filter(i -> i.parentStep() != null && i.parentStep().id().equals(parentStepId))
                .findFirst();
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
                endDate, old.parentStep(), old.parentInstance());
        this.created.set(index, updated);
        return updated;
    }

    @Override
    public ProcessInstance updateVariables(final UUID id, final Map<String, Object> variables)
            throws NoSuchProcessInstanceException {
        final int index = this.index(id);
        final ProcessInstance old = this.created.get(index);
        final ProcessInstance updated = new Instance(id, old.processDefinition(), Map.copyOf(variables),
                old.startDate(), old.endDate(), old.parentStep(), old.parentInstance());
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

    public List<ProcessInstance> created() {
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
     * @param parentStep the parent step
     * @param parentInstance the parent instance as it was when this instance was created
     */
    record Instance(UUID id, ProcessDefinition processDefinition, Map<String, Object> variables, Date startDate,
            Date endDate, ProcessStep parentStep, ProcessInstance parentInstance) implements ProcessInstance {
    }
}
