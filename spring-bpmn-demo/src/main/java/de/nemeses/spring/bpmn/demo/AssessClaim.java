package de.nemeses.spring.bpmn.demo;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import de.nemeses.spring.bpmn.exec.BpmnNodeContext;
import de.nemeses.spring.bpmn.exec.BpmnNodeImplementation;
import de.nemeses.spring.bpmn.exec.ProcessEngineException;

/**
 * Service task {@code assessClaim} of the sample process {@code claimHandling}: reads the variables {@code amount}
 * (a number, missing counts as {@code 0}), {@code policyActive} (a boolean, missing counts as {@code true}) and
 * {@code documents} (a collection, missing counts as empty) and stores them with these defaults. It sets
 * {@code covered} to {@code policyActive}, {@code approved} to {@code covered} and {@code expertReviewRequired} to
 * whether {@code amount} exceeds {@value #EXPERT_REVIEW_LIMIT}. A variable of another type fails the task.
 */
@Component("assessClaim")
class AssessClaim implements BpmnNodeImplementation {

    /** Amount above which a claim needs the review of an expert. */
    static final int EXPERT_REVIEW_LIMIT = 1000;

    @Override
    public void execute(final BpmnNodeContext context) throws ProcessEngineException {
        final Object amount = context.variables().getOrDefault("amount", 0);
        if (!(amount instanceof final Number number)) {
            throw new ProcessEngineException("Variable 'amount' is not a number: " + amount);
        }
        final Object policyActive = context.variables().getOrDefault("policyActive", Boolean.TRUE);
        if (!(policyActive instanceof final Boolean covered)) {
            throw new ProcessEngineException("Variable 'policyActive' is not a boolean: " + policyActive);
        }
        final Object documents = context.variables().getOrDefault("documents", List.of());
        if (!(documents instanceof Collection)) {
            throw new ProcessEngineException("Variable 'documents' is not a collection: " + documents);
        }
        final boolean expertReviewRequired = new BigDecimal(number.toString())
                .compareTo(BigDecimal.valueOf(EXPERT_REVIEW_LIMIT)) > 0;
        context.setVariable("amount", number);
        context.setVariable("policyActive", covered);
        context.setVariable("documents", documents);
        context.setVariable("covered", covered);
        context.setVariable("approved", covered);
        context.setVariable("expertReviewRequired", expertReviewRequired);
        LoggerFactory.getLogger(this.getClass()).info(
                "Claim of instance {} with amount {}: covered: {}, expert review required: {}",
                context.processInstanceId(), number, covered, expertReviewRequired);
    }
}
