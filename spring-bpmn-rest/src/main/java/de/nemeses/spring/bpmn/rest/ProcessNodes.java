package de.nemeses.spring.bpmn.rest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import de.nemeses.spring.bpmn.ProcessDefinitionRegistrar;
import de.nemeses.spring.bpmn.exec.ProcessDefinitionModelCache;
import de.nemeses.spring.bpmn.exec.ProcessEngineException;
import de.nemeses.spring.bpmn.model.bpmn20.FlowElement;
import de.nemeses.spring.bpmn.model.bpmn20.FlowNode;
import de.nemeses.spring.bpmn.model.bpmn20.Process;
import de.nemeses.spring.bpmn.model.bpmn20.RootElement;
import de.nemeses.spring.bpmn.model.bpmn20.SubProcess;
import de.nemeses.spring.bpmn.repository.ProcessDefinition;
import jakarta.xml.bind.JAXBElement;

/**
 * Reads the flow nodes of a process definition from its cached BPMN model, because a {@link
 * de.nemeses.spring.bpmn.repository.ProcessStep} stores only the id of its node. The type of a node is the local name
 * of its BPMN element (e.g. {@code userTask}, {@code serviceTask}, {@code exclusiveGateway}).
 */
@Component
class ProcessNodes {

    private final ProcessDefinitionModelCache models;

    ProcessNodes(final ProcessDefinitionModelCache models) {
        this.models = models;
    }

    /**
     * @param definition a process definition version
     * @return the flow nodes of its process by node id, including those inside (nested) sub-processes
     * @throws ProcessEngineException if the BPMN document cannot be parsed or has no process of the definition name
     */
    Map<String, Node> nodes(final ProcessDefinition definition) throws ProcessEngineException {
        for (final JAXBElement<? extends RootElement> element : this.models.document(definition).getRootElement()) {
            if (element.getValue() instanceof final Process process
                    && definition.name().equals(ProcessDefinitionRegistrar.processName(process))) {
                final Map<String, Node> nodes = new HashMap<>();
                ProcessNodes.collect(process.getFlowElement(), nodes);
                return nodes;
            }
        }
        throw new ProcessEngineException("Process definition '" + definition.name()
                + "' contains no process of that name.");
    }

    private static void collect(final List<JAXBElement<? extends FlowElement>> elements,
            final Map<String, Node> nodes) {
        for (final JAXBElement<? extends FlowElement> element : elements) {
            if (element.getValue() instanceof final FlowNode node) {
                nodes.put(node.getId(), new Node(element.getName().getLocalPart(), node.getName()));
            }
            if (element.getValue() instanceof final SubProcess subProcess) {
                ProcessNodes.collect(subProcess.getFlowElement(), nodes);
            }
        }
    }

    /**
     * A flow node of the model.
     *
     * @param type the local name of the BPMN element, e.g. {@code userTask}
     * @param name the {@code name} attribute, {@code null} if it has none
     */
    record Node(
            String type,
            String name) {
    }
}
