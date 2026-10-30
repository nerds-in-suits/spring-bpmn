package de.nemeses.spring.bpmn;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import de.nemeses.spring.bpmn.repository.NoSuchProcessInstanceException;
import de.nemeses.spring.bpmn.repository.NoSuchProcessStepException;
import de.nemeses.spring.bpmn.repository.ProcessStep;
import de.nemeses.spring.bpmn.repository.ProcessStepRepository;

/**
 * Keeps created process steps in memory, in creation order.
 */
class InMemoryProcessStepRepository implements ProcessStepRepository {

    private final InMemoryProcessInstanceRepository instances;
    private final List<ProcessStep> created = new ArrayList<>();

    InMemoryProcessStepRepository(final InMemoryProcessInstanceRepository instances) {
        this.instances = instances;
    }

    @Override
    public ProcessStep create(final UUID instanceId, final String nodeId, final String incomingFlowId,
            final UUID parentStepId) throws NoSuchProcessInstanceException {
        this.instances.retrieve(instanceId);
        final ProcessStep step = new Step(UUID.randomUUID(), instanceId, nodeId, incomingFlowId, parentStepId,
                new Date(), null);
        this.created.add(step);
        return step;
    }

    @Override
    public ProcessStep retrieve(final UUID id) throws NoSuchProcessStepException {
        return this.created.get(this.index(id));
    }

    @Override
    public List<ProcessStep> pending(final UUID instanceId, final String nodeId) {
        return this.created.stream()
                .filter(step -> step.processInstanceId().equals(instanceId) && step.nodeId().equals(nodeId)
                        && step.endDate() == null)
                .toList();
    }

    @Override
    public List<ProcessStep> children(final UUID parentStepId) {
        return this.created.stream().filter(step -> parentStepId.equals(step.parentStepId())).toList();
    }

    @Override
    public long countPending(final UUID instanceId) {
        return this.created.stream()
                .filter(step -> step.processInstanceId().equals(instanceId) && step.endDate() == null)
                .count();
    }

    @Override
    public void complete(final Collection<UUID> ids, final Date endDate) throws NoSuchProcessStepException {
        for (final UUID id : ids) {
            final int index = this.index(id);
            final ProcessStep old = this.created.get(index);
            this.created.set(index, new Step(id, old.processInstanceId(), old.nodeId(), old.incomingFlowId(),
                    old.parentStepId(), old.startDate(), endDate));
        }
    }

    @Override
    public List<ProcessStep> steps(final UUID instanceId) {
        return this.created.stream().filter(step -> step.processInstanceId().equals(instanceId)).toList();
    }

    private int index(final UUID id) throws NoSuchProcessStepException {
        for (int i = 0; i < this.created.size(); i++) {
            if (this.created.get(i).id().equals(id)) {
                return i;
            }
        }
        throw new NoSuchProcessStepException(id);
    }

    /**
     * A created step.
     *
     * @param id the id
     * @param processInstanceId the instance id
     * @param nodeId the node id
     * @param incomingFlowId the incoming flow id
     * @param parentStepId the parent step id
     * @param startDate the start date
     * @param endDate the end date
     */
    record Step(UUID id, UUID processInstanceId, String nodeId, String incomingFlowId, UUID parentStepId,
            Date startDate, Date endDate) implements ProcessStep {
    }
}
