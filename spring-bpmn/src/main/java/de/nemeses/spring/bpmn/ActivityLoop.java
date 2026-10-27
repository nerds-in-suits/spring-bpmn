package de.nemeses.spring.bpmn;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import de.nemeses.spring.bpmn.model.bpmn20.Activity;
import de.nemeses.spring.bpmn.model.bpmn20.LoopCharacteristics;
import de.nemeses.spring.bpmn.model.bpmn20.MultiInstanceLoopCharacteristics;
import de.nemeses.spring.bpmn.model.bpmn20.StandardLoopCharacteristics;

/**
 * Runs the body of an activity once, or repeatedly according to its {@code standardLoopCharacteristics}, for the
 * {@link BpmnNodeExecutor}.
 * <p>
 * All iterations of a standard loop run in the execution of one step (one transaction); the loop counter is not
 * stored. An iteration runs only while the number of iterations run so far ({@code loopCounter}) is less than
 * {@code loopMaximum} (if given; so {@code loopMaximum="0"} runs no iteration) and, except before the first iteration
 * if {@code testBefore} is {@code false}, while the {@code loopCondition} is true. Without {@code loopCondition}
 * the activity runs {@code loopMaximum} times; without both the loop is rejected. The {@code loopCondition} is evaluated
 * by the {@link ConditionEvaluator} against the variables as changed by the iterations so far plus the variable
 * {@value #LOOP_COUNTER} (which hides a process variable of the same name in the condition).
 * {@code multiInstanceLoopCharacteristics} are not supported yet.
 */
@Component
class ActivityLoop {

    /** Name of the variable holding the number of completed iterations in a {@code loopCondition}. */
    static final String LOOP_COUNTER = "loopCounter";

    private final ConditionEvaluator conditions;

    ActivityLoop(final ConditionEvaluator conditions) {
        this.conditions = conditions;
    }

    /**
     * Runs the body of an activity once, or as its standard loop says.
     *
     * @param activity the activity
     * @param language the default expression language of the document
     * @param variables the process variables before the activity
     * @param body one execution of the activity
     * @return the variables after the last iteration (the given ones if no iteration ran)
     * @throws ProcessEngineException if the loop characteristics are not supported or invalid, the loop condition
     *             cannot be evaluated or the body failed
     */
    Map<String, Object> run(final Activity activity, final String language, final Map<String, Object> variables,
            final Iteration body) throws ProcessEngineException {
        final LoopCharacteristics loop = activity.getLoopCharacteristics() == null
                ? null
                : activity.getLoopCharacteristics().getValue();
        return switch (loop) {
        case null -> body.execute(0, variables);
        case final StandardLoopCharacteristics standard -> this.standard(activity, standard, language, variables, body);
        case MultiInstanceLoopCharacteristics _ -> throw new ProcessEngineException("Activity '" + activity.getId()
                + "' has multi-instance loop characteristics, which are not supported yet.");
        default -> throw new ProcessEngineException("Activity '" + activity.getId() + "' has "
                + loop.getClass().getSimpleName() + ", which are not supported yet.");
        };
    }

    private Map<String, Object> standard(final Activity activity, final StandardLoopCharacteristics loop,
            final String language, final Map<String, Object> variables, final Iteration body)
            throws ProcessEngineException {
        final BigInteger maximum = ActivityLoop.maximum(activity, loop);
        Map<String, Object> current = variables;
        int counter = 0;
        while (BigInteger.valueOf(counter).compareTo(maximum) < 0
                && (counter == 0 && !loop.isTestBefore()
                        || this.isConditionTrue(activity, loop, language, current, counter))) {
            current = body.execute(counter, current);
            counter++;
        }
        LoggerFactory.getLogger(this.getClass()).debug("Loop of activity {} ran {} times", activity.getId(), counter);
        return current;
    }

    /**
     * @return the {@code loopMaximum}, or {@link Integer#MAX_VALUE} if there is none (the loop counter is an
     *         {@code int})
     */
    private static BigInteger maximum(final Activity activity, final StandardLoopCharacteristics loop)
            throws ProcessEngineException {
        final BigInteger maximum = loop.getLoopMaximum();
        if (maximum == null) {
            if (loop.getLoopCondition() == null) {
                throw new ProcessEngineException("Activity '" + activity.getId()
                        + "' has a standard loop without loopCondition and loopMaximum, which would never end.");
            }
            return BigInteger.valueOf(Integer.MAX_VALUE);
        }
        if (maximum.signum() < 0) {
            throw new ProcessEngineException("Activity '" + activity.getId() + "' has a negative loopMaximum "
                    + maximum + ".");
        }
        return maximum;
    }

    private boolean isConditionTrue(final Activity activity, final StandardLoopCharacteristics loop,
            final String language, final Map<String, Object> variables, final int counter)
            throws ProcessEngineException {
        if (loop.getLoopCondition() == null) {
            return true;
        }
        final Map<String, Object> visible = new LinkedHashMap<>(variables);
        visible.put(LOOP_COUNTER, counter);
        return this.conditions.evaluate("loop condition of activity '" + activity.getId() + "'",
                loop.getLoopCondition(), language, visible);
    }

    /**
     * One execution of an activity's body.
     */
    @FunctionalInterface
    interface Iteration {

        /**
         * @param loopCounter the number of iterations completed before this one ({@code 0} without loop)
         * @param variables the process variables as changed by the previous iterations
         * @return the process variables after this iteration
         * @throws ProcessEngineException if the activity failed
         */
        Map<String, Object> execute(int loopCounter, Map<String, Object> variables) throws ProcessEngineException;
    }
}
