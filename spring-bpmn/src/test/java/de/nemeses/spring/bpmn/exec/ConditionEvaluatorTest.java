package de.nemeses.spring.bpmn.exec;

import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.expression.ExpressionException;

import de.nemeses.spring.bpmn.model.bpmn20.Expression;
import de.nemeses.spring.bpmn.model.bpmn20.FormalExpression;
import de.nemeses.spring.bpmn.model.bpmn20.SequenceFlow;

/**
 * Evaluates sequence flow conditions with {@link ConditionEvaluator}.
 */
class ConditionEvaluatorTest {

    private static final Map<String, Object> VARIABLES = Map.of("amount", 3, "order",
            Map.of("customer", "ACME", "items", 2), "approved", Boolean.TRUE);

    private final ConditionEvaluator evaluator = new ConditionEvaluator();

    private static SequenceFlow flow(final Expression condition, final String text) {
        condition.getContent().add(text);
        final SequenceFlow flow = new SequenceFlow();
        flow.setId("f");
        flow.setConditionExpression(condition);
        return flow;
    }

    private boolean evaluate(final String text) throws ProcessEngineException {
        return this.evaluator.evaluate(ConditionEvaluatorTest.flow(new Expression(), text),
                ConditionEvaluator.BPMN_DEFAULT_LANGUAGE, VARIABLES);
    }

    private void assertFails(final String text) {
        Assertions.assertThrows(ProcessEngineException.class, () -> this.evaluate(text));
    }

    @Test
    void readsVariables() throws ProcessEngineException {
        Assertions.assertTrue(this.evaluate("amount == 3"));
        Assertions.assertTrue(this.evaluate("${amount > 2 and approved}"));
        Assertions.assertTrue(this.evaluate("  ${ #amount == 3 }  "));
        Assertions.assertTrue(this.evaluate("['amount'] == 3"));
        Assertions.assertTrue(this.evaluate("order.customer == 'ACME' && order.items < amount"));
        Assertions.assertFalse(this.evaluate("approved == false"));
    }

    @Test
    void usesFormalExpressionLanguageBeforeDefault() throws ProcessEngineException {
        final FormalExpression spel = new FormalExpression();
        spel.setLanguage(ConditionEvaluator.SPEL_LANGUAGE);
        Assertions.assertTrue(this.evaluator.evaluate(ConditionEvaluatorTest.flow(spel, "approved"), "urn:other",
                VARIABLES));
        Assertions.assertTrue(this.evaluator.evaluate(ConditionEvaluatorTest.flow(new FormalExpression(), "approved"),
                ConditionEvaluator.SPEL_LANGUAGE, VARIABLES));

        final FormalExpression other = new FormalExpression();
        other.setLanguage("urn:other");
        Assertions.assertThrows(ProcessEngineException.class, () -> this.evaluator.evaluate(
                ConditionEvaluatorTest.flow(other, "approved"), ConditionEvaluator.SPEL_LANGUAGE, VARIABLES));
    }

    @Test
    void invalidConditionsFail() {
        this.assertFails("");
        this.assertFails("${}");
        this.assertFails("amount >");
        this.assertFails("amount");
        this.assertFails("missing == 1");
        this.assertFails("null");
    }

    @Test
    void restrictedContextRejectsTypesAssignmentsAndMethods() {
        for (final String text : new String[] {"T(java.lang.Boolean).TRUE", "(amount = 4) == 4",
            "new java.lang.Boolean(true)", "order.isEmpty()", "@bean == null" }) {
            final ProcessEngineException e = Assertions.assertThrows(ProcessEngineException.class,
                    () -> this.evaluate(text), text);
            Assertions.assertInstanceOf(ExpressionException.class, e.getCause(), text);
        }
        Assertions.assertEquals(3, VARIABLES.get("amount"));
    }
}
