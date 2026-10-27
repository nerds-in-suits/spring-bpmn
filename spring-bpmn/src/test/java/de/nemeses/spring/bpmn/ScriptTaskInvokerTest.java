package de.nemeses.spring.bpmn;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import de.nemeses.spring.bpmn.model.bpmn20.Script;
import de.nemeses.spring.bpmn.model.bpmn20.ScriptTask;

/**
 * Runs scripts of script tasks with {@link ScriptTaskInvoker}.
 */
class ScriptTaskInvokerTest {

    private static final Map<String, Object> VARIABLES = Map.of("amount", 3, "order",
            Map.of("customer", "ACME", "items", 2), "approved", Boolean.TRUE);

    private final ScriptTaskInvoker invoker = new ScriptTaskInvoker();

    private static ScriptTask task(final String format, final String text) {
        final ScriptTask task = new ScriptTask();
        task.setId("st");
        task.setScriptFormat(format);
        if (text != null) {
            final Script script = new Script();
            script.getContent().add(text);
            task.setScript(script);
        }
        return task;
    }

    private NodeContext run(final String format, final String text) throws ProcessEngineException {
        final NodeContext context = new NodeContext(UUID.randomUUID(), "st", VARIABLES);
        this.invoker.execute(ScriptTaskInvokerTest.task(format, text), context);
        return context;
    }

    private NodeContext run(final String text) throws ProcessEngineException {
        return this.run(null, text);
    }

    private void assertFails(final String text) {
        Assertions.assertThrows(ProcessEngineException.class, () -> this.run(text), text);
    }

    @Test
    void assignsVariablesInOrder() throws ProcessEngineException {
        final NodeContext context = this.run("amount = amount * 2;\n total = amount + #amount + ['amount'];"
                + " count = 1; count++; customer = order.customer; ;");
        Assertions.assertTrue(context.isChanged());
        Assertions.assertEquals(Map.of("amount", 6, "total", 18, "count", 2, "customer", "ACME", "order",
                Map.of("customer", "ACME", "items", 2), "approved", Boolean.TRUE), context.changedVariables());
    }

    @Test
    void readOnlyScriptChangesNothing() throws ProcessEngineException {
        Assertions.assertFalse(this.run("amount > 2 and approved").isChanged());
        Assertions.assertFalse(this.run(" ").isChanged());
        Assertions.assertFalse(this.run(null).isChanged());
    }

    @Test
    void acceptsSpelFormat() throws ProcessEngineException {
        Assertions.assertTrue(this.run(ConditionEvaluator.SPEL_LANGUAGE, "x = 1").isChanged());
        Assertions.assertThrows(ProcessEngineException.class, () -> this.run("groovy", "x = 1"));
        Assertions.assertThrows(ProcessEngineException.class,
                () -> this.run(ConditionEvaluator.BPMN_DEFAULT_LANGUAGE, "x = 1"));
    }

    @Test
    void splitsStatementsOutsideStringLiterals() {
        Assertions.assertEquals(List.of("a = 'x;y'", "b = \"it\"\";\"", "c = 'it''s;'"),
                ScriptTaskInvoker.statements(" a = 'x;y'; b = \"it\"\";\";\n c = 'it''s;' ;"));
        Assertions.assertEquals(List.of(), ScriptTaskInvoker.statements(" ; \n;"));
    }

    @Test
    void invalidOrForbiddenStatementsFail() {
        for (final String text : new String[] {"amount >", "missing + 1", "x = null", "#amount = 1",
            "['amount'] = 1", "order.items = 1", "order['items'] = 1", "#x++", "T(java.lang.System).exit(1)",
            "x = new java.util.ArrayList()", "x = order.isEmpty()", "x = @bean", "x = 1; y = missing" }) {
            this.assertFails(text);
        }
        Assertions.assertEquals(2, ((Map<?, ?>) VARIABLES.get("order")).get("items"));
    }
}
