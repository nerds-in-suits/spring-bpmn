package de.nemeses.spring.bpmn.exec.loop;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.LoggerFactory;

import de.nemeses.spring.bpmn.exec.BpmnNodeTask;
import de.nemeses.spring.bpmn.exec.ConditionEvaluator;
import de.nemeses.spring.bpmn.exec.FlowSelector;
import de.nemeses.spring.bpmn.exec.Invoker;
import de.nemeses.spring.bpmn.exec.NodeExecution;
import de.nemeses.spring.bpmn.exec.ProcessEngineException;
import de.nemeses.spring.bpmn.model.bpmn20.Activity;
import de.nemeses.spring.bpmn.model.bpmn20.BaseElement;
import de.nemeses.spring.bpmn.model.bpmn20.DataInput;
import de.nemeses.spring.bpmn.model.bpmn20.DataOutput;
import de.nemeses.spring.bpmn.model.bpmn20.FlowNode;
import de.nemeses.spring.bpmn.model.bpmn20.MultiInstanceFlowCondition;
import de.nemeses.spring.bpmn.model.bpmn20.MultiInstanceLoopCharacteristics;
import de.nemeses.spring.bpmn.model.bpmn20.SequenceFlow;
import de.nemeses.spring.bpmn.repository.NoSuchProcessInstanceException;
import de.nemeses.spring.bpmn.repository.NoSuchProcessStepException;
import de.nemeses.spring.bpmn.repository.ProcessInstance;
import de.nemeses.spring.bpmn.repository.ProcessInstanceRepository;
import de.nemeses.spring.bpmn.repository.ProcessStep;
import de.nemeses.spring.bpmn.repository.ProcessStepRepository;
import de.nemeses.spring.tasks.TaskService;

/**
 * Wraps the {@link Invoker} of an activity type ({@link MultiInstanceLoopEnhancer}) and runs the activities with
 * {@code multiInstanceLoopCharacteristics} with one {@link ProcessStep} per instance; other activities are passed on
 * to the delegate unchanged.
 * <p>
 * The step that reaches the activity is the <em>activity step</em>. Its first execution determines the number of
 * instances, either by evaluating {@code loopCardinality} (SpEL, must yield a non-negative integer) or as the size of
 * the collection in the process variable named by the local part of {@code loopDataInputRef} (exactly one of both).
 * It stores the number of instances and a copy of the collection in its own loop data
 * ({@link ProcessStepRepository#updateLoop}) and creates the <em>instance steps</em> at the same node, with the
 * activity step as {@link ProcessStep#parentStepId() parent}, their index as {@link ProcessStep#loopCounter()} and
 * their element of the collection as {@link ProcessStep#loopItem()}: all at once if parallel, else only the first one.
 * The activity step stays pending, so the instance does not end while the activity runs. Without instances, the
 * activity step completes at once and takes the outgoing flows.
 * <p>
 * Each instance step runs the delegate once, with a view of the execution ({@link NodeExecution#forLoopInstance})
 * that has the index and element of the instance and no outgoing flows; the delegate must complete the instance step
 * (an activity that waits, like a {@code callActivity}, is not supported). The element is also visible as the
 * variable named by the {@code inputDataItem} (its {@code name}, else its {@code id}); this variable and the one named
 * by the {@code outputDataItem} are local to the instance and not stored as process variables (a process variable of
 * the same name is hidden and kept). All other variable changes are stored at once. The value of the
 * {@code outputDataItem} variable at the end of an instance, if set, is kept in the internal process variable
 * {@code <activity id>}{@value #OUTPUTS_SUFFIX} (instance index to value) until the activity completes; then the
 * process variable named by the local part of {@code loopDataOutputRef} is set to the list of these values in index
 * order and the internal variable is removed.
 * <p>
 * After each instance, the {@code completionCondition} (if any) is evaluated against the process variables plus
 * {@code loopCounter} (the index of the instance), {@code numberOfInstances}, {@code numberOfActiveInstances} (the
 * pending instance steps), {@code numberOfCompletedInstances} and {@code numberOfTerminatedInstances} (always
 * {@code 0} when evaluated). If it is true, or all instances are completed, the pending instance steps are completed
 * without running (their tasks are skipped later), the activity step is completed and the outgoing flows of the
 * activity are taken once, in the scope of the activity step ({@link NodeExecution#scope(UUID)}). Otherwise a
 * sequential loop creates the step of the next instance, a parallel loop waits for its other instances.
 * <p>
 * All of this happens in the transaction of one step, under the lock of the process instance, and the instance steps
 * are read after locking. So the instance steps of a parallel loop complete one after the other, exactly one of them
 * sees the loop finished and continues, and a step completed without running is skipped.
 *
 * @param <T> the type of activity
 */
@SuppressWarnings({"PMD.CouplingBetweenObjects", "PMD.ExcessiveImports"}) // BPMN loop model, repositories, tasks
class MultiInstanceLoop<T extends FlowNode> implements Invoker<T> {

    /** Suffix of the internal process variable collecting the outputs of the instances. */
    static final String OUTPUTS_SUFFIX = ".loopOutputs";

    /** Name of the variable holding the index of the instance in a {@code completionCondition}. */
    private static final String LOOP_COUNTER = "loopCounter";

    private final Invoker<T> delegate;
    private final ProcessInstanceRepository instances;
    private final ProcessStepRepository steps;
    private final TaskService taskService;
    private final ConditionEvaluator conditions;
    private final FlowSelector flows;

    MultiInstanceLoop(final Invoker<T> delegate, final ProcessInstanceRepository instances,
            final ProcessStepRepository steps, final TaskService taskService, final ConditionEvaluator conditions,
            final FlowSelector flows) {
        this.delegate = delegate;
        this.instances = instances;
        this.steps = steps;
        this.taskService = taskService;
        this.conditions = conditions;
        this.flows = flows;
    }

    @Override
    public Class<T> nodeType() {
        return this.delegate.nodeType();
    }

    /**
     * Executes the activity step or an instance step of a multi-instance activity, or passes any other activity on
     * to the delegate. For an instance step the flows are taken in the scope of the activity step.
     *
     * @param node the activity
     * @param execution the step being executed
     * @return the flows to take; none while the loop runs
     * @throws ProcessEngineException if the loop characteristics are not supported or invalid, an expression cannot be
     *             evaluated, the delegate failed or a step or the instance does not exist
     */
    @Override
    public Collection<SequenceFlow> invoke(final T node, final NodeExecution execution) throws ProcessEngineException {
        try {
            final Activity activity = (Activity) node;
            if (activity.getLoopCharacteristics() == null
                    || !(activity.getLoopCharacteristics().getValue()
                            instanceof final MultiInstanceLoopCharacteristics characteristics)) {
                return this.delegate.invoke(node, execution);
            }
            MultiInstanceLoop.validate(activity, characteristics);
            final ProcessStep step = execution.step();
            final Loop loop = new Loop(execution.instance(), activity, characteristics, execution.outgoing(),
                    execution.language());
            if (step.parentStepId() != null) {
                final ProcessStep parent = this.steps.retrieve(step.parentStepId());
                if (parent.nodeId().equals(step.nodeId())) {
                    execution.scope(parent.parentStepId());
                    return this.runInstance(loop, node, execution, parent);
                }
            }
            return this.start(loop, step);
        } catch (final NoSuchProcessStepException | NoSuchProcessInstanceException e) {
            throw new ProcessEngineException("Cannot execute multi-instance activity '" + node.getId()
                    + "' of process instance " + execution.instance().id() + ".", e);
        }
    }

    private static void validate(final Activity activity, final MultiInstanceLoopCharacteristics loop)
            throws ProcessEngineException {
        final String prefix = "Multi-instance activity '" + activity.getId() + "' ";
        if (loop.getBehavior() != MultiInstanceFlowCondition.ALL || !loop.getComplexBehaviorDefinition().isEmpty()) {
            throw new ProcessEngineException(prefix + "has behavior " + loop.getBehavior().value()
                    + " or a complexBehaviorDefinition, which is not supported yet.");
        }
        if ((loop.getLoopCardinality() == null) == (loop.getLoopDataInputRef() == null)) {
            throw new ProcessEngineException(prefix + "needs either loopCardinality or loopDataInputRef.");
        }
        if (loop.getInputDataItem() != null && loop.getLoopDataInputRef() == null) {
            throw new ProcessEngineException(prefix + "has an inputDataItem without loopDataInputRef.");
        }
        if ((loop.getOutputDataItem() == null) != (loop.getLoopDataOutputRef() == null)) {
            throw new ProcessEngineException(prefix + "needs both or neither of outputDataItem and loopDataOutputRef.");
        }
    }

    /**
     * First execution of the activity step: creates the instance steps.
     */
    private Collection<SequenceFlow> start(final Loop loop, final ProcessStep step)
            throws ProcessEngineException, NoSuchProcessStepException, NoSuchProcessInstanceException {
        if (!this.steps.children(step.id()).isEmpty()) {
            LoggerFactory.getLogger(this.getClass()).info(
                    "Skipping step {} (multi-instance activity {}) of process instance {}, which has already started.",
                    step.id(), loop.activity().getId(), loop.instance().id());
            return List.of();
        }
        final List<Object> items = this.items(loop);
        final int count = items == null ? this.cardinality(loop) : items.size();
        this.steps.updateLoop(step.id(), count, items);
        LoggerFactory.getLogger(this.getClass()).debug("Multi-instance activity {} of process instance {} runs {} {}"
                + " instances", loop.activity().getId(), loop.instance().id(), count,
                loop.characteristics().isIsSequential() ? "sequential" : "parallel");
        if (count == 0) {
            return this.finish(loop, step, new LinkedHashMap<>(loop.instance().variables()),
                    loop.instance().variables());
        }
        for (int index = 0; index < (loop.characteristics().isIsSequential() ? 1 : count); index++) {
            this.createInstance(loop, step, index, items);
        }
        return List.of();
    }

    /**
     * @return a copy of the input collection, or {@code null} with {@code loopCardinality}
     */
    private List<Object> items(final Loop loop) throws ProcessEngineException {
        if (loop.characteristics().getLoopDataInputRef() == null) {
            return null;
        }
        final String name = loop.characteristics().getLoopDataInputRef().getLocalPart();
        final Object value = loop.instance().variables().get(name);
        if (value instanceof final Collection<?> collection) {
            return new ArrayList<>(collection);
        }
        throw new ProcessEngineException("The loopDataInputRef of multi-instance activity '"
                + loop.activity().getId() + "' names variable '" + name + "', which is no collection but " + value
                + ".");
    }

    private int cardinality(final Loop loop) throws ProcessEngineException {
        final String description = "loop cardinality of activity '" + loop.activity().getId() + "'";
        final Object value = this.conditions.value(description, loop.characteristics().getLoopCardinality(),
                loop.language(), loop.instance().variables());
        final BigInteger count = switch (value) {
        case final Integer number -> BigInteger.valueOf(number);
        case final Long number -> BigInteger.valueOf(number);
        case final Short number -> BigInteger.valueOf(number);
        case final Byte number -> BigInteger.valueOf(number);
        case final BigInteger number -> number;
        case null, default -> null;
        };
        if (count == null || count.signum() < 0 || count.bitLength() >= Integer.SIZE) {
            throw new ProcessEngineException("The " + description + " did not yield a non-negative int but " + value
                    + ".");
        }
        return count.intValue();
    }

    private void createInstance(final Loop loop, final ProcessStep activityStep, final int index,
            final List<Object> items) throws NoSuchProcessInstanceException {
        final ProcessStep step = this.steps.create(loop.instance().id(), activityStep.nodeId(),
                activityStep.incomingFlowId(), activityStep.id(), index, items == null ? null : items.get(index));
        this.taskService.trigger(BpmnNodeTask.BEAN_NAME, step.id());
    }

    /**
     * Runs one instance step and completes the loop if it is finished.
     */
    private Collection<SequenceFlow> runInstance(final Loop loop, final T node, final NodeExecution execution,
            final ProcessStep activityStep)
            throws ProcessEngineException, NoSuchProcessStepException, NoSuchProcessInstanceException {
        final ProcessStep step = execution.step();
        final int index = step.loopCounter();
        final Map<String, Object> stored = this.runDelegate(loop, node, execution);
        final Map<String, Object> variables = this.withoutLocals(loop, step, stored);

        final int count = activityStep.loopCounter();
        // read after locking: the instance steps completed by other executions of this loop are seen
        final Collection<ProcessStep> children = this.steps.children(activityStep.id());
        final Collection<UUID> pending = children.stream()
                .filter(child -> child.endDate() == null && !child.id().equals(step.id()))
                .map(ProcessStep::id)
                .toList();
        final int created = children.size();
        final boolean all = pending.isEmpty() && created == count;
        if (all || this.isCompletionConditionTrue(loop, variables, index, count, pending.size(),
                created - pending.size())) {
            this.steps.complete(pending, new Date());
            LoggerFactory.getLogger(this.getClass()).debug(
                    "Multi-instance activity {} of process instance {} completed after instance {}, {} instances"
                            + " not run", loop.activity().getId(), loop.instance().id(), index,
                    count - created + pending.size());
            return this.finish(loop, activityStep, variables, stored);
        }
        this.store(loop.instance(), variables, stored);
        if (loop.characteristics().isIsSequential()) {
            @SuppressWarnings("unchecked")
            final List<Object> items = (List<Object>) activityStep.loopItem();
            this.createInstance(loop, activityStep, index + 1, items);
        }
        return List.of();
    }

    /**
     * Runs the delegate for one instance step, which must complete it.
     *
     * @return the process variables as stored after the delegate
     */
    private Map<String, Object> runDelegate(final Loop loop, final T node, final NodeExecution execution)
            throws ProcessEngineException, NoSuchProcessInstanceException {
        final ProcessStep step = execution.step();
        final String inputName = MultiInstanceLoop.name(loop.characteristics().getInputDataItem());
        final Map<String, Object> visible = new LinkedHashMap<>(loop.instance().variables());
        if (inputName != null && step.loopItem() != null) {
            visible.put(inputName, step.loopItem());
        }
        final NodeExecution instanceExecution = execution.forLoopInstance(visible, step.loopCounter(),
                step.loopItem());
        this.delegate.invoke(node, instanceExecution);
        if (!instanceExecution.isCompleted()) {
            throw new ProcessEngineException("Multi-instance activity '" + loop.activity().getId()
                    + "' does not complete its instance in one step, which is not supported yet.");
        }
        return this.instances.retrieve(loop.instance().id()).variables();
    }

    /**
     * @return the process variables after an instance, without its local data items, with its output collected
     */
    private Map<String, Object> withoutLocals(final Loop loop, final ProcessStep step,
            final Map<String, Object> stored) {
        final Map<String, Object> before = loop.instance().variables();
        final String inputName = MultiInstanceLoop.name(loop.characteristics().getInputDataItem());
        final String outputName = MultiInstanceLoop.name(loop.characteristics().getOutputDataItem());
        final Map<String, Object> variables = new LinkedHashMap<>(stored);
        final Object output = outputName == null ? null : variables.get(outputName);
        MultiInstanceLoop.restore(variables, before, inputName);
        MultiInstanceLoop.restore(variables, before, outputName);
        if (output != null) {
            final String key = loop.activity().getId() + OUTPUTS_SUFFIX;
            final Map<String, Object> outputs = variables.get(key) instanceof final Map<?, ?> collected
                    ? MultiInstanceLoop.stringKeys(collected)
                    : new LinkedHashMap<>();
            outputs.put(String.valueOf(step.loopCounter()), output);
            variables.put(key, outputs);
        }
        return variables;
    }

    /**
     * Resets an instance-local variable to its value before the instance (or removes it).
     */
    private static void restore(final Map<String, Object> variables, final Map<String, Object> before,
            final String local) {
        if (local == null) {
            return;
        }
        if (before.containsKey(local)) {
            variables.put(local, before.get(local));
        } else {
            variables.remove(local);
        }
    }

    private boolean isCompletionConditionTrue(final Loop loop, final Map<String, Object> variables, final int index,
            final int count, final int active, final int completed) throws ProcessEngineException {
        if (loop.characteristics().getCompletionCondition() == null) {
            return false;
        }
        final Map<String, Object> visible = new LinkedHashMap<>(variables);
        visible.put(LOOP_COUNTER, index);
        visible.put("numberOfInstances", count);
        visible.put("numberOfActiveInstances", active);
        visible.put("numberOfCompletedInstances", completed);
        visible.put("numberOfTerminatedInstances", 0);
        return this.conditions.evaluate("completion condition of activity '" + loop.activity().getId() + "'",
                loop.characteristics().getCompletionCondition(), loop.language(), visible);
    }

    /**
     * Collects the outputs, stores the variables, completes the activity step and selects the outgoing flows.
     */
    private Collection<SequenceFlow> finish(final Loop loop, final ProcessStep activityStep,
            final Map<String, Object> variables, final Map<String, Object> stored)
            throws ProcessEngineException, NoSuchProcessStepException, NoSuchProcessInstanceException {
        if (loop.characteristics().getLoopDataOutputRef() != null) {
            final Object collected = variables.remove(loop.activity().getId() + OUTPUTS_SUFFIX);
            final Map<String, Object> outputs = collected instanceof final Map<?, ?> map
                    ? MultiInstanceLoop.stringKeys(map)
                    : Map.of();
            final List<Object> list = outputs.entrySet().stream()
                    .sorted((a, b) -> Integer.compare(Integer.parseInt(a.getKey()), Integer.parseInt(b.getKey())))
                    .map(Map.Entry::getValue)
                    .toList();
            variables.put(loop.characteristics().getLoopDataOutputRef().getLocalPart(), new ArrayList<>(list));
        }
        this.store(loop.instance(), variables, stored);
        final Collection<SequenceFlow> taken = this.flows.activity(loop.activity(), loop.outgoing(), loop.language(),
                variables);
        this.steps.complete(List.of(activityStep.id()), new Date());
        return taken;
    }

    /**
     * Stores the variables if they differ from the stored ones.
     */
    private void store(final ProcessInstance instance, final Map<String, Object> variables,
            final Map<String, Object> stored) throws NoSuchProcessInstanceException {
        if (!variables.equals(stored)) {
            this.instances.updateVariables(instance.id(), variables);
        }
    }

    private static Map<String, Object> stringKeys(final Map<?, ?> map) {
        final Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    /**
     * @return the {@code name} of the data item, else its {@code id}; {@code null} without data item
     */
    private static String name(final BaseElement item) {
        final String name = switch (item) {
        case null -> null;
        case final DataInput input -> input.getName();
        case final DataOutput output -> output.getName();
        default -> null;
        };
        return name == null && item != null ? item.getId() : name;
    }

    /**
     * What one execution needs to know about the loop.
     *
     * @param instance the (locked) process instance, with its variables before the step
     * @param activity the activity
     * @param characteristics its loop characteristics
     * @param outgoing its outgoing flows
     * @param language the default expression language of the document
     */
    private record Loop(ProcessInstance instance, Activity activity, MultiInstanceLoopCharacteristics characteristics,
            Collection<SequenceFlow> outgoing, String language) {
    }
}
