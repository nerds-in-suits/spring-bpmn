package de.nemeses.spring.bpmn.demo;

import java.math.BigDecimal;

import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import de.nemeses.spring.bpmn.exec.BpmnNodeContext;
import de.nemeses.spring.bpmn.exec.BpmnNodeImplementation;
import de.nemeses.spring.bpmn.exec.ProcessEngineException;

/**
 * Service task {@code checkOrder} of the sample process {@code orderApproval}: sets the variable
 * {@code approvalRequired} to whether the variable {@code amount} exceeds {@value #APPROVAL_LIMIT}. A missing
 * {@code amount} counts as {@code 0}; an {@code amount} that is not a number fails the task.
 */
@Component("checkOrder")
class CheckOrder implements BpmnNodeImplementation {

    /** Amount above which an order needs a manual approval. */
    static final int APPROVAL_LIMIT = 100;

    @Override
    public void execute(final BpmnNodeContext context) throws ProcessEngineException {
        final Object amount = context.variables().getOrDefault("amount", 0);
        if (!(amount instanceof final Number number)) {
            throw new ProcessEngineException("Variable 'amount' is not a number: " + amount);
        }
        final boolean approvalRequired = new BigDecimal(number.toString())
                .compareTo(BigDecimal.valueOf(APPROVAL_LIMIT)) > 0;
        context.setVariable("approvalRequired", approvalRequired);
        LoggerFactory.getLogger(this.getClass()).info("Order of instance {} with amount {}: approval required: {}",
                context.processInstanceId(), number, approvalRequired);
    }
}
