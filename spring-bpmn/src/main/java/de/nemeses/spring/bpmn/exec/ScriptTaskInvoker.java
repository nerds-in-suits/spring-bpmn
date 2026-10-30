package de.nemeses.spring.bpmn.exec;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.expression.AccessException;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionException;
import org.springframework.expression.PropertyAccessor;
import org.springframework.expression.TypedValue;
import org.springframework.expression.spel.SpelNode;
import org.springframework.expression.spel.ast.Assign;
import org.springframework.expression.spel.ast.OpDec;
import org.springframework.expression.spel.ast.OpInc;
import org.springframework.expression.spel.ast.PropertyOrFieldReference;
import org.springframework.expression.spel.standard.SpelExpression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.DataBindingPropertyAccessor;
import org.springframework.expression.spel.support.MapAccessor;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Component;

import de.nemeses.spring.bpmn.model.bpmn20.Script;
import de.nemeses.spring.bpmn.model.bpmn20.ScriptTask;

/**
 * Runs the {@code script} of a {@code scriptTask} as Spring Expression Language (SpEL) statements against the process
 * variables.
 * <p>
 * The {@code scriptFormat} must be absent or {@link ConditionEvaluator#SPEL_LANGUAGE}; any other format fails. The
 * script consists of SpEL expressions separated by {@code ;} (outside of string literals), evaluated in order; empty
 * statements are ignored, so is a missing or blank script. Their results are ignored.
 * <p>
 * Variables are read like in conditions (see {@link ConditionEvaluator}): by name, via the root map
 * ({@code ['amount']}) or as SpEL variables ({@code #amount}); nested values through map keys or getters. Every read
 * sees the changes of the preceding statements. A variable is written only by assigning to its plain name
 * ({@code amount = amount * 2}, {@code total = amount + 1}, {@code count++}); the value is passed to
 * {@link BpmnNodeContext#setVariable}; a ternary or Elvis operator on the right side must be parenthesized
 * ({@code size = (amount > 5 ? 'big' : 'small')}). Other assignment targets ({@code #amount = 1}, {@code ['amount'] = 1},
 * {@code order.items = 1}), assigning {@code null} and removing variables are not supported. The evaluation context is
 * a {@link SimpleEvaluationContext}: no type references, constructors, bean references or method calls.
 */
@Component
class ScriptTaskInvoker {

    private static final char SEPARATOR = ';';

    private final SpelExpressionParser parser = new SpelExpressionParser();

    /**
     * Runs the script of the task; the variable changes are collected in the context.
     *
     * @param task the script task
     * @param context the context of the node, receives the changed variables
     * @throws ProcessEngineException if the script format is not supported or a statement is invalid, not allowed or
     *             fails
     */
    void execute(final ScriptTask task, final NodeContext context) throws ProcessEngineException {
        final String format = task.getScriptFormat();
        if (format != null && !ConditionEvaluator.SPEL_LANGUAGE.equals(format.strip())) {
            throw new ProcessEngineException("Script task '" + task.getId() + "' uses script format '" + format
                    + "', which is not supported; use " + ConditionEvaluator.SPEL_LANGUAGE + ".");
        }
        final List<String> statements = ScriptTaskInvoker.statements(ScriptTaskInvoker.text(task.getScript()));
        final List<SpelExpression> expressions = new ArrayList<>();
        for (final String statement : statements) {
            expressions.add(this.parse(task, statement));
        }
        final Map<String, Object> root = new ScriptVariables(context);
        final EvaluationContext evaluation = SimpleEvaluationContext
                .forPropertyAccessors(new VariableAccessor(), new MapAccessor(false),
                        DataBindingPropertyAccessor.forReadOnlyAccess())
                .withRootObject(root)
                .build();
        context.variables().forEach(evaluation::setVariable);
        for (final SpelExpression expression : expressions) {
            try {
                expression.getValue(evaluation);
            } catch (final ExpressionException e) {
                throw new ProcessEngineException("Cannot evaluate statement '" + expression.getExpressionString()
                        + "' of script task '" + task.getId() + "'.", e);
            }
        }
    }

    private SpelExpression parse(final ScriptTask task, final String statement) throws ProcessEngineException {
        final SpelExpression expression;
        try {
            expression = this.parser.parseRaw(statement);
        } catch (final ExpressionException e) {
            throw new ProcessEngineException("Cannot parse statement '" + statement + "' of script task '"
                    + task.getId() + "'.", e);
        }
        ScriptTaskInvoker.checkAssignments(task, statement, expression.getAST());
        return expression;
    }

    /**
     * Rejects assignments, increments and decrements whose target is not a plain variable name.
     */
    private static void checkAssignments(final ScriptTask task, final String statement, final SpelNode node)
            throws ProcessEngineException {
        if ((node instanceof Assign || node instanceof OpInc || node instanceof OpDec)
                && !(node.getChild(0) instanceof PropertyOrFieldReference)) {
            throw new ProcessEngineException("Statement '" + statement + "' of script task '" + task.getId()
                    + "' assigns to '" + node.getChild(0).toStringAST()
                    + "'; only plain variable names can be assigned.");
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            ScriptTaskInvoker.checkAssignments(task, statement, node.getChild(i));
        }
    }

    private static String text(final Script script) {
        if (script == null) {
            return "";
        }
        return script.getContent().stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .collect(Collectors.joining());
    }

    /**
     * Splits the script at {@code ;} outside of SpEL string literals ({@code '...'} and {@code "..."}, in which a
     * doubled quote is an escaped quote).
     *
     * @return the non-blank statements, stripped
     */
    static List<String> statements(final String script) {
        final List<String> statements = new ArrayList<>();
        final StringBuilder current = new StringBuilder();
        char quote = 0;
        for (int i = 0; i < script.length(); i++) {
            final char c = script.charAt(i);
            if (quote == 0 && c == SEPARATOR) {
                ScriptTaskInvoker.addStatement(statements, current);
                continue;
            }
            if (quote == 0 && (c == '\'' || c == '"')) {
                quote = c;
            } else if (c == quote) {
                quote = 0;
            }
            current.append(c);
        }
        ScriptTaskInvoker.addStatement(statements, current);
        return statements;
    }

    private static void addStatement(final List<String> statements, final StringBuilder current) {
        final String statement = current.toString().strip();
        if (!statement.isEmpty()) {
            statements.add(statement);
        }
        current.setLength(0);
    }

    /**
     * Root object of the evaluation: a read-only map view of the node's current variables.
     */
    private static final class ScriptVariables extends AbstractMap<String, Object> {

        private final NodeContext context;

        ScriptVariables(final NodeContext context) {
            this.context = context;
        }

        @Override
        public Set<Entry<String, Object>> entrySet() {
            return this.context.variables().entrySet();
        }

        @Override
        public Object get(final Object key) {
            return this.context.variables().get(key);
        }

        @Override
        public boolean containsKey(final Object key) {
            return this.context.variables().containsKey(key);
        }
    }

    /**
     * Reads and writes the variables of the {@link ScriptVariables} root by name.
     */
    private static final class VariableAccessor implements PropertyAccessor {

        @Override
        public Class<?>[] getSpecificTargetClasses() {
            return new Class<?>[] {ScriptVariables.class };
        }

        @Override
        public boolean canRead(final EvaluationContext evaluation, final Object target, final String name) {
            return target instanceof ScriptVariables;
        }

        @Override
        public TypedValue read(final EvaluationContext evaluation, final Object target, final String name)
                throws AccessException {
            final Map<String, Object> variables = (ScriptVariables) target;
            if (variables == null || !variables.containsKey(name)) {
                throw new AccessException("Unknown variable '" + name + "'.");
            }
            return new TypedValue(variables.get(name));
        }

        @Override
        public boolean canWrite(final EvaluationContext evaluation, final Object target, final String name) {
            return target instanceof ScriptVariables;
        }

        @Override
        public void write(final EvaluationContext evaluation, final Object target, final String name,
                final Object newValue) throws AccessException {
            if (newValue == null) {
                throw new AccessException("Variable '" + name + "' cannot be set to null.");
            }
            ((ScriptVariables) target).context.setVariable(name, newValue);
            evaluation.setVariable(name, newValue);
        }
    }
}
