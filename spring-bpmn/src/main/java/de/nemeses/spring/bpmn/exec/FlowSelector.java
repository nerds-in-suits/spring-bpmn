package de.nemeses.spring.bpmn.exec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import de.nemeses.spring.bpmn.model.bpmn20.Activity;
import de.nemeses.spring.bpmn.model.bpmn20.ExclusiveGateway;
import de.nemeses.spring.bpmn.model.bpmn20.SequenceFlow;

/**
 * Selects the outgoing sequence flows of a node to take, evaluating their conditions with the
 * {@link ConditionEvaluator}.
 */
@Component
class FlowSelector {

    private final ConditionEvaluator conditions;

    FlowSelector(final ConditionEvaluator conditions) {
        this.conditions = conditions;
    }

    /**
     * @param gatewayId the id of the exclusive gateway
     * @param defaultFlow its {@code default} flow ({@link ExclusiveGateway#getDefault()}), may be {@code null}
     * @param outgoing the flows leaving the gateway, in document order
     * @param language the default expression language of the document
     * @param variables the process variables
     * @return the first non-default flow without condition or whose condition is true, else the default flow
     * @throws ProcessEngineException if there is no such flow or a condition cannot be evaluated
     */
    SequenceFlow exclusive(final String gatewayId, final Object defaultFlow, final List<SequenceFlow> outgoing,
            final String language, final Map<String, Object> variables) throws ProcessEngineException {
        for (final SequenceFlow flow : outgoing) {
            if (!flow.equals(defaultFlow) && this.isTrue(flow, language, variables)) {
                return flow;
            }
        }
        if (defaultFlow instanceof final SequenceFlow flow && outgoing.contains(flow)) {
            return flow;
        }
        throw new ProcessEngineException("Exclusive gateway '" + gatewayId
                + "' has no outgoing flow whose condition is true and no default flow.");
    }

    /**
     * @param activity the activity
     * @param outgoing the flows leaving the activity
     * @param language the default expression language of the document
     * @param variables the process variables (after the changes of the activity)
     * @return the flows out of an activity to take: the unconditional ones and those whose condition is true, or the
     *         default flow instead of the latter if no condition is true
     * @throws ProcessEngineException if a condition cannot be evaluated
     */
    List<SequenceFlow> activity(final Activity activity, final List<SequenceFlow> outgoing, final String language,
            final Map<String, Object> variables) throws ProcessEngineException {
        final List<SequenceFlow> taken = new ArrayList<>();
        boolean conditionTrue = false;
        for (final SequenceFlow flow : outgoing) {
            if (flow.equals(activity.getDefault())) {
                continue;
            }
            if (flow.getConditionExpression() == null) {
                taken.add(flow);
            } else if (this.conditions.evaluate(flow, language, variables)) {
                taken.add(flow);
                conditionTrue = true;
            }
        }
        if (!conditionTrue && activity.getDefault() instanceof final SequenceFlow defaultFlow
                && outgoing.contains(defaultFlow)) {
            taken.add(defaultFlow);
        }
        return taken;
    }

    /**
     * @param flows the flows leaving a start event or parallel gateway
     * @return all flows
     * @throws ProcessEngineException if a flow has a condition
     */
    static List<SequenceFlow> unconditional(final List<SequenceFlow> flows) throws ProcessEngineException {
        for (final SequenceFlow flow : flows) {
            if (flow.getConditionExpression() != null) {
                throw new ProcessEngineException("Sequence flow '" + flow.getId()
                        + "' has a condition, which is not supported yet.");
            }
        }
        return flows;
    }

    private boolean isTrue(final SequenceFlow flow, final String language, final Map<String, Object> variables)
            throws ProcessEngineException {
        return flow.getConditionExpression() == null || this.conditions.evaluate(flow, language, variables);
    }
}
