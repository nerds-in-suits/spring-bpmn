package de.nemeses.spring.bpmn.demo;

import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import de.nemeses.spring.bpmn.exec.BpmnNodeContext;
import de.nemeses.spring.bpmn.exec.BpmnNodeImplementation;

/**
 * Service task {@code greet} of the sample process {@code helloWorld}: sets the variable {@code greeting} to
 * {@code "Hello, <name>!"}, with the variable {@code name} or {@code World} if it is missing.
 */
@Component("greet")
class Greet implements BpmnNodeImplementation {

    @Override
    public void execute(final BpmnNodeContext context) {
        final String greeting = "Hello, " + context.variables().getOrDefault("name", "World") + "!";
        context.setVariable("greeting", greeting);
        LoggerFactory.getLogger(this.getClass()).info("Instance {}: {}", context.processInstanceId(), greeting);
    }
}
