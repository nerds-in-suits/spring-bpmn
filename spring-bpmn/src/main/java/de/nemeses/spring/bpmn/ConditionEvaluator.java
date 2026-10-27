package de.nemeses.spring.bpmn;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionException;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.DataBindingPropertyAccessor;
import org.springframework.expression.spel.support.MapAccessor;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Component;

import de.nemeses.spring.bpmn.model.bpmn20.Expression;
import de.nemeses.spring.bpmn.model.bpmn20.FormalExpression;
import de.nemeses.spring.bpmn.model.bpmn20.SequenceFlow;

/**
 * Evaluates the {@code conditionExpression} of sequence flows as Spring Expression Language (SpEL) expressions against
 * the process variables.
 * <p>
 * The language of a condition is the {@code language} attribute of its {@code formalExpression}, else the
 * {@code expressionLanguage} of the {@code definitions} element. Accepted are {@link #SPEL_LANGUAGE} and the BPMN
 * default {@link #BPMN_DEFAULT_LANGUAGE}, which is what a document without {@code expressionLanguage} reports (the
 * engine cannot tell it from an explicit XPath declaration and evaluates it as SpEL, too). Any other language fails.
 * <p>
 * The expression text may be wrapped in {@code ${...}}. Variables are read by name ({@code amount > 5}), via the root
 * map ({@code ['amount']}) or as SpEL variables ({@code #amount}); properties of variable values are read through their
 * getters or map keys. The evaluation context is a read-only {@link SimpleEvaluationContext}: no assignment, type
 * references, constructors, bean references or method calls. The result must be a {@link Boolean}.
 */
@Component
public class ConditionEvaluator {

    /** Expression language URI selecting Spring SpEL for conditions. */
    public static final String SPEL_LANGUAGE = BpmnNodeExecutor.NAMESPACE + "/spel";

    /** Default {@code expressionLanguage} of BPMN {@code definitions}; evaluated as SpEL. */
    public static final String BPMN_DEFAULT_LANGUAGE = "http://www.w3.org/1999/XPath";

    private static final String TEMPLATE_PREFIX = "${";
    private static final String TEMPLATE_SUFFIX = "}";

    private final ExpressionParser parser = new SpelExpressionParser();

    ConditionEvaluator() {
        // package-private for Spring
    }

    /**
     * @param flow a sequence flow with a {@code conditionExpression}
     * @param defaultLanguage the {@code expressionLanguage} of the {@code definitions} element
     * @param variables the process variables
     * @return whether the condition is true
     * @throws ProcessEngineException if the language is not supported, the expression is empty or invalid, cannot be
     *             evaluated (e.g. unknown variable) or does not yield a {@link Boolean}
     */
    public boolean evaluate(final SequenceFlow flow, final String defaultLanguage, final Map<String, Object> variables)
            throws ProcessEngineException {
        final Expression condition = flow.getConditionExpression();
        final String language = condition instanceof final FormalExpression formal && formal.getLanguage() != null
                ? formal.getLanguage()
                : defaultLanguage;
        if (!SPEL_LANGUAGE.equals(language) && !BPMN_DEFAULT_LANGUAGE.equals(language)) {
            throw new ProcessEngineException("Condition of sequence flow '" + flow.getId() + "' uses expression language '"
                    + language + "', which is not supported; use " + SPEL_LANGUAGE + ".");
        }
        final String text = ConditionEvaluator.text(condition);
        if (text.isEmpty()) {
            throw new ProcessEngineException("Condition of sequence flow '" + flow.getId() + "' is empty.");
        }
        final Map<String, Object> root = Collections.unmodifiableMap(variables);
        final SimpleEvaluationContext.Builder builder = SimpleEvaluationContext
                .forPropertyAccessors(new MapAccessor(false), DataBindingPropertyAccessor.forReadOnlyAccess())
                .withAssignmentDisabled()
                .withRootObject(root);
        final EvaluationContext context = builder.build();
        root.forEach(context::setVariable);
        final Object result;
        try {
            result = this.parser.parseExpression(text).getValue(context);
        } catch (final ExpressionException e) {
            throw new ProcessEngineException("Cannot evaluate condition '" + text + "' of sequence flow '" + flow.getId()
                    + "'.", e);
        }
        if (result instanceof final Boolean value) {
            return value;
        }
        throw new ProcessEngineException("Condition '" + text + "' of sequence flow '" + flow.getId()
                + "' did not yield a boolean but " + result + ".");
    }

    private static String text(final Expression condition) {
        final String text = condition.getContent().stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .collect(Collectors.joining())
                .strip();
        if (text.startsWith(TEMPLATE_PREFIX) && text.endsWith(TEMPLATE_SUFFIX)) {
            return text.substring(TEMPLATE_PREFIX.length(), text.length() - TEMPLATE_SUFFIX.length()).strip();
        }
        return text;
    }
}
