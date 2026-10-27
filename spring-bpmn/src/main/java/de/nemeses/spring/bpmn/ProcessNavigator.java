package de.nemeses.spring.bpmn;

import java.util.List;
import java.util.function.Function;

import de.nemeses.spring.bpmn.model.bpmn20.FlowElement;
import de.nemeses.spring.bpmn.model.bpmn20.FlowNode;
import de.nemeses.spring.bpmn.model.bpmn20.Process;
import de.nemeses.spring.bpmn.model.bpmn20.SequenceFlow;
import jakarta.xml.bind.JAXBElement;

/**
 * Finds flow nodes and sequence flows in a parsed BPMN {@link Process}.
 */
final class ProcessNavigator {

    private ProcessNavigator() {
        // static helpers only
    }

    /**
     * @param process the process
     * @param nodeId the id of the flow node
     * @return the flow node
     * @throws ProcessEngineException if the process has no flow node with that id
     */
    static FlowNode flowNode(final Process process, final String nodeId) throws ProcessEngineException {
        for (final JAXBElement<? extends FlowElement> element : process.getFlowElement()) {
            if (element.getValue() instanceof final FlowNode node && nodeId.equals(node.getId())) {
                return node;
            }
        }
        throw new ProcessEngineException("Process '" + ProcessDefinitionRegistrar.processName(process)
                + "' has no flow node '" + nodeId + "'.");
    }

    /**
     * @param process the process
     * @param node the flow node
     * @return the sequence flows leaving the node
     */
    static List<SequenceFlow> outgoing(final Process process, final FlowNode node) {
        return ProcessNavigator.flows(process, node, SequenceFlow::getSourceRef);
    }

    /**
     * @param process the process
     * @param node the flow node
     * @return the sequence flows arriving at the node
     */
    static List<SequenceFlow> incoming(final Process process, final FlowNode node) {
        return ProcessNavigator.flows(process, node, SequenceFlow::getTargetRef);
    }

    private static List<SequenceFlow> flows(final Process process, final FlowNode node,
            final Function<SequenceFlow, Object> end) {
        return process.getFlowElement().stream()
                .map(JAXBElement::getValue)
                .filter(SequenceFlow.class::isInstance)
                .map(SequenceFlow.class::cast)
                .filter(flow -> end.apply(flow) instanceof final FlowElement element
                        && node.getId().equals(element.getId()))
                .toList();
    }
}
