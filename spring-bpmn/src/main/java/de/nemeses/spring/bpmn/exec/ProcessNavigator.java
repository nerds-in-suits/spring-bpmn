package de.nemeses.spring.bpmn.exec;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import de.nemeses.spring.bpmn.ProcessDefinitionRegistrar;
import de.nemeses.spring.bpmn.model.bpmn20.FlowElement;
import de.nemeses.spring.bpmn.model.bpmn20.FlowNode;
import de.nemeses.spring.bpmn.model.bpmn20.Process;
import de.nemeses.spring.bpmn.model.bpmn20.SequenceFlow;
import de.nemeses.spring.bpmn.model.bpmn20.StartEvent;
import de.nemeses.spring.bpmn.model.bpmn20.SubProcess;
import jakarta.xml.bind.JAXBElement;

/**
 * Finds flow nodes and sequence flows in a parsed BPMN {@link Process}, including those inside (nested) embedded
 * sub-processes. Element ids are unique within a document and a sequence flow connects nodes of the scope (process or
 * sub-process) it is defined in, so all lookups search the whole process.
 */
final class ProcessNavigator {

    private ProcessNavigator() {
        // static helpers only
    }

    /**
     * @param process the process
     * @param nodeId the id of the flow node
     * @return the flow node, at the top level of the process or inside a (nested) sub-process
     * @throws ProcessEngineException if the process has no flow node with that id
     */
    static FlowNode flowNode(final Process process, final String nodeId) throws ProcessEngineException {
        return ProcessNavigator.elements(process.getFlowElement())
                .filter(FlowNode.class::isInstance)
                .map(FlowNode.class::cast)
                .filter(node -> nodeId.equals(node.getId()))
                .findFirst()
                .orElseThrow(() -> new ProcessEngineException("Process '"
                        + ProcessDefinitionRegistrar.processName(process) + "' has no flow node '" + nodeId + "'."));
    }

    /**
     * @param subProcess the sub-process
     * @return the ids of the start events directly inside the sub-process (not in nested ones), in document order
     */
    static List<String> startEvents(final SubProcess subProcess) {
        return subProcess.getFlowElement().stream()
                .map(JAXBElement::getValue)
                .filter(StartEvent.class::isInstance)
                .map(FlowElement::getId)
                .toList();
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
        return ProcessNavigator.elements(process.getFlowElement())
                .filter(SequenceFlow.class::isInstance)
                .map(SequenceFlow.class::cast)
                .filter(flow -> end.apply(flow) instanceof final FlowElement element
                        && node.getId().equals(element.getId()))
                .toList();
    }

    /**
     * @param elements the flow elements of a process or sub-process
     * @return the elements, each followed (depth first) by the elements of a sub-process
     */
    private static Stream<FlowElement> elements(final List<JAXBElement<? extends FlowElement>> elements) {
        return elements.stream()
                .<FlowElement>map(JAXBElement::getValue)
                .flatMap(element -> element instanceof final SubProcess subProcess
                        ? Stream.concat(Stream.of(element), ProcessNavigator.elements(subProcess.getFlowElement()))
                        : Stream.of(element));
    }
}
