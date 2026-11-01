package de.nemeses.spring.bpmn.exec.task;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import de.nemeses.spring.bpmn.exec.BpmnNodeContext;
import de.nemeses.spring.bpmn.exec.BpmnNodeImplementation;

/**
 * The {@link BpmnNodeContext} passed by the {@link TaskRunner} to a {@link BpmnNodeImplementation} or a script;
 * collects the variable changes.
 */
final class NodeContext implements BpmnNodeContext {

    private final UUID instanceId;
    private final String node;
    private final Map<String, Object> vars;
    private final int counter;
    private final Object item;
    private boolean changed;

    NodeContext(final UUID instanceId, final String node, final Map<String, Object> variables,
            final int loopCounter) {
        this(instanceId, node, variables, loopCounter, null);
    }

    NodeContext(final UUID instanceId, final String node, final Map<String, Object> variables,
            final int loopCounter, final Object loopItem) {
        this.instanceId = instanceId;
        this.node = node;
        this.vars = new LinkedHashMap<>(variables);
        this.counter = loopCounter;
        this.item = loopItem;
    }

    @Override
    public UUID processInstanceId() {
        return this.instanceId;
    }

    @Override
    public String nodeId() {
        return this.node;
    }

    @Override
    public int loopCounter() {
        return this.counter;
    }

    @Override
    public Object loopItem() {
        return this.item;
    }

    @Override
    public Map<String, Object> variables() {
        return Collections.unmodifiableMap(this.vars);
    }

    @Override
    public void setVariable(final String name, final Object value) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");
        this.vars.put(name, value);
        this.changed = true;
    }

    @Override
    public void removeVariable(final String name) {
        if (this.vars.remove(name) != null) {
            this.changed = true;
        }
    }

    /**
     * @return whether {@link #setVariable} or {@link #removeVariable} changed a variable
     */
    boolean isChanged() {
        return this.changed;
    }

    /**
     * @return a copy of the variables including the changes
     */
    Map<String, Object> changedVariables() {
        return new LinkedHashMap<>(this.vars);
    }
}
