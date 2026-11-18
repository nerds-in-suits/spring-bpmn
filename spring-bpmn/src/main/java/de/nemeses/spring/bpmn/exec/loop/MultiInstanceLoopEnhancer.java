package de.nemeses.spring.bpmn.exec.loop;

import de.nemeses.spring.bpmn.exec.ConditionEvaluator;
import de.nemeses.spring.bpmn.exec.FlowSelector;
import de.nemeses.spring.bpmn.exec.Invoker;
import de.nemeses.spring.bpmn.exec.InvokerEnhancer;
import de.nemeses.spring.bpmn.model.bpmn20.Activity;
import de.nemeses.spring.bpmn.model.bpmn20.FlowNode;
import de.nemeses.spring.bpmn.repository.ProcessInstanceRepository;
import de.nemeses.spring.bpmn.repository.ProcessStepRepository;

/**
 * Enhances the invoker of every activity type with a {@link MultiInstanceLoop}, which runs the activities with
 * {@code multiInstanceLoopCharacteristics} once per instance and passes all other activities on unchanged.
 */
class MultiInstanceLoopEnhancer implements InvokerEnhancer {

    private final ProcessInstanceRepository instances;
    private final ProcessStepRepository steps;
    private final ConditionEvaluator conditions;
    private final FlowSelector flows;

    MultiInstanceLoopEnhancer(final ProcessInstanceRepository instances, final ProcessStepRepository steps,
            final ConditionEvaluator conditions, final FlowSelector flows) {
        this.instances = instances;
        this.steps = steps;
        this.conditions = conditions;
        this.flows = flows;
    }

    @Override
    public <T extends FlowNode> Invoker<T> enhance(final Invoker<T> invoker) {
        if (!Activity.class.isAssignableFrom(invoker.nodeType())) {
            return invoker;
        }
        return new MultiInstanceLoop<>(invoker, this.instances, this.steps, this.conditions, this.flows);
    }
}
