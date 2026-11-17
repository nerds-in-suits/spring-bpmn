package de.nemeses.spring.bpmn.demo;

import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import de.nemeses.spring.bpmn.exec.BpmnNodeContext;
import de.nemeses.spring.bpmn.exec.BpmnNodeImplementation;

/**
 * Service task {@code notifyCustomer} of the sample process {@code claimHandling}: logs the variables
 * {@code decision} and {@code payout} in place of a letter to the customer and sets {@code customerNotified} to
 * {@code true}.
 */
@Component("notifyCustomer")
class NotifyCustomer implements BpmnNodeImplementation {

    @Override
    public void execute(final BpmnNodeContext context) {
        LoggerFactory.getLogger(this.getClass()).info("Claim of instance {} {}, payout {}",
                context.processInstanceId(), context.variables().get("decision"), context.variables().get("payout"));
        context.setVariable("customerNotified", true);
    }
}
