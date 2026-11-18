import { BpmnModdle } from 'bpmn-moddle';
import { assign, map, pick } from 'min-dash';

class DiFactory {
  constructor(moddle) {
    this.moddle = moddle;
  }

  create(type, attrs) {
    return this.moddle.create(type, attrs || {});
  }

  createDiBounds(bounds) {
    return this.create('dc:Bounds', bounds);
  }

  createDiLabel(bounds) {
    return this.create('bpmndi:BPMNLabel', {
      bounds: this.createDiBounds(bounds)
    });
  }

  createDiShape(semantic, bounds, attrs) {
    return this.create('bpmndi:BPMNShape', assign({
      bpmnElement: semantic,
      bounds: this.createDiBounds(bounds)
    }, attrs));
  }

  createDiWaypoints(waypoints) {
    var self = this;

    return map(waypoints, function(pos) {
      return self.createDiWaypoint(pos);
    });
  }

  createDiWaypoint(point) {
    return this.create('dc:Point', pick(point, [ 'x', 'y' ]));
  }

  createDiEdge(semantic, waypoints, attrs) {
    return this.create('bpmndi:BPMNEdge', assign({
      bpmnElement: semantic,
      waypoint: this.createDiWaypoints(waypoints)
    }, attrs));
  }

  createDiPlane(attrs) {
    return this.create('bpmndi:BPMNPlane', attrs);
  }

  createDiDiagram(attrs) {
    return this.create('bpmndi:BPMNDiagram', attrs);
  }
}

const DEFAULT_TASK_HEIGHT = 80;
const DEFAULT_TASK_WIDTH = 100;

function getDefaultSize(element) {
  if (is(element, 'bpmn:Activity')) {
    return { width: DEFAULT_TASK_WIDTH, height: DEFAULT_TASK_HEIGHT };
  }

  if (is(element, 'bpmn:Gateway')) {
    return { width: 50, height: 50 };
  }

  if (is(element, 'bpmn:Event')) {
    return { width: 36, height: 36 };
  }

  if (is(element, 'bpmn:Participant')) {
    return { width: 300, height: 60 };
  }

  if (is(element, 'bpmn:Lane')) {
    return { width: 300, height: 60 };
  }

  if (is(element, 'bpmn:DataObjectReference')) {
    return { width: 36, height: 50 };
  }

  if (is(element, 'bpmn:DataStoreReference')) {
    return { width: 50, height: 50 };
  }

  if (is(element, 'bpmn:TextAnnotation')) {
    return { width: 100, height: 40 };
  }

  return null;
}

function is(element, type) {
  return !!element && element.$instanceOf(type);
}

/**
 * A layout-relevant BPMN structural error.
 */
class LayoutError extends Error {
  constructor(code, elementId, message, relatedElementIds = []) {
    super(message);

    this.name = 'LayoutError';
    this.code = code;
    this.elementId = elementId;
    this.relatedElementIds = relatedElementIds;
  }
}

/**
 * A non-fatal layout diagnostic.
 */
class LayoutWarning extends Error {
  constructor(code, elementId, message, relatedElementIds = []) {
    super(message);

    this.name = 'LayoutWarning';
    this.code = code;
    this.elementId = elementId;
    this.relatedElementIds = relatedElementIds;
  }
}

const HORIZONTAL_GAP = 100;
const VERTICAL_GAP = 80;
const OUTER_MARGIN = 80;
const SUB_PROCESS_PADDING = 40;
const ROUTING_MARGIN = 20;
const MESSAGE_FLOW_BEND_PENALTY = VERTICAL_GAP;
const PARTICIPANT_HEADER_WIDTH = 30;
const LANE_CONTENT_PADDING = 40;
const ANNOTATION_MIN_WIDTH = 100;
const ANNOTATION_MAX_WIDTH = 340;
const ANNOTATION_WIDTH_STEP = 40;
const ANNOTATION_CHARACTER_WIDTH = 7;
const ANNOTATION_LINE_HEIGHT = 14;
const ANNOTATION_PADDING = 10;
const GROUP_PADDING = 40;
const EXTERNAL_LABEL_WIDTH = 90;
const EXTERNAL_LABEL_LINE_HEIGHT = 14;
const EXTERNAL_LABEL_HORIZONTAL_PADDING = 2;
const EXTERNAL_LABEL_CHARACTER_WIDTH = 7;
const EXTERNAL_LABEL_UPPERCASE_WIDTH = 9;
const EXTERNAL_LABEL_WIDE_CHARACTER_WIDTH = 11;
const EXTERNAL_LABEL_SPACE_WIDTH = 4;
const EXTERNAL_LABEL_CLEARANCE = 5;
const FLOW_LABEL_INDENT = 15;

// Participant, sub-process, and lane sizing policy.
const MIN_PARTICIPANT_WIDTH = 300;
const MIN_PARTICIPANT_HEIGHT = 150;
const MIN_SUB_PROCESS_WIDTH = 140;
const MIN_SUB_PROCESS_HEIGHT = 120;

// Distinct from VERTICAL_GAP despite the equal current value: this is the
// vertical extent of a semantic band, not an inter-shape gap.
const SEMANTIC_BAND_HEIGHT = 80;
const MIN_LANE_CONTENT_WIDTH = 300;
const MIN_LANE_HEIGHT = 60;
const BOUNDARY_EVENT_SPACING = 8;

// Text annotation sizing policy.
const ANNOTATION_MIN_HEIGHT = 40;
const ANNOTATION_TARGET_ASPECT_RATIO = 3;
const ANNOTATION_ASPECT_RATIO_PENALTY_SCALE = 100;

// Artifact placement search policy.
const MAX_ARTIFACT_SEARCH_OFFSET = 400;
const MAX_ARTIFACT_GAP_STEPS = 4;
const NON_STRAIGHT_ARTIFACT_ASSOCIATION_PENALTY = VERTICAL_GAP;
const BOUNDARY_EVENT_ARTIFACT_CLEARANCE = 3 * ROUTING_MARGIN;
const EXPANDED_SUBPROCESS_ANNOTATION_CLEARANCE = 2 * ROUTING_MARGIN;
const EXPANDED_SUBPROCESS_LABEL_HEIGHT = 28;
const EXPANDED_SUBPROCESS_LABEL_PADDING = 7;
const MAX_LABEL_SEARCH_STEPS = 100;

// Message flow channel policy. Kept as distinct constants from each other
// despite equal current values because they represent independent policies:
// the docking offset from a node's center versus the spacing between
// parallel message flow channels.
const MESSAGE_FLOW_SIDE_OFFSET = 10;
const MESSAGE_FLOW_CHANNEL_SPACING = 10;
const MESSAGE_FLOW_CHANNEL_WIDTH_DIVISOR = 4;
const MAX_EXHAUSTIVE_PARTICIPANT_COUNT = 8;
const MESSAGE_FLOW_OBSTACLE_INSET = 1;

// Sequence flow routing search policy.
const MAX_ROUTE_SEARCH_ATTEMPTS = 100;
const MAX_LOCAL_U_CHANNEL_ATTEMPTS = 20;

// Distinct from MESSAGE_FLOW_OBSTACLE_INSET despite the equal current value:
// this inset applies to sequence flow obstacle checks, not message flows.
const ROUTE_OBSTACLE_INSET = 1;
const VISIBILITY_GRAPH_TURN_PENALTY = 1;
const SEGMENT_INTERSECTION_EPSILON = 1e-6;

function isArtifact(element) {
  return is(element, 'bpmn:TextAnnotation') ||
    is(element, 'bpmn:DataObjectReference') ||
    is(element, 'bpmn:DataStoreReference') ||
    is(element, 'bpmn:Group');
}

function isExteriorArtifact(element) {
  return is(element, 'bpmn:TextAnnotation') ||
    is(element, 'bpmn:DataStoreReference');
}

function isExternalLabelOwner(element) {
  return is(element, 'bpmn:Event') ||
    is(element, 'bpmn:Gateway') ||
    is(element, 'bpmn:DataStoreReference') ||
    is(element, 'bpmn:DataObjectReference') ||
    is(element, 'bpmn:SequenceFlow') ||
    is(element, 'bpmn:MessageFlow') ||
    is(element, 'bpmn:Group');
}

function getExternalLabelText(element) {
  if (is(element, 'bpmn:Group')) {
    return element.categoryValueRef?.value || '';
  }

  return element.name || '';
}

function hasSubProcessLabel(element) {
  return is(element, 'bpmn:SubProcess') && !!element.name?.trim();
}

function hasEventDefinition(event, type) {
  return (event.eventDefinitions || []).some(definition => is(definition, type));
}

function isSupportedVisualElement(element) {
  return is(element, 'bpmn:Activity') ||
    is(element, 'bpmn:Event') ||
    (is(element, 'bpmn:Gateway') && !is(element, 'bpmn:ComplexGateway')) ||
    isArtifact(element) ||
    is(element, 'bpmn:Participant') ||
    is(element, 'bpmn:Lane');
}

function isSupportedVisualConnection(element) {
  return is(element, 'bpmn:SequenceFlow') ||
    is(element, 'bpmn:MessageFlow') ||
    is(element, 'bpmn:Association') ||
    is(element, 'bpmn:DataAssociation');
}

function getExpandedIds(definitions, root) {
  const diagram = (definitions.diagrams || []).find(candidate => candidate.plane?.bpmnElement === root);
  const ids = new Set();

  for (const element of diagram?.plane?.planeElement || []) {
    if (element.$instanceOf('bpmndi:BPMNShape') && element.isExpanded === true) {
      ids.add(element.bpmnElement.id);
    }
  }

  return ids;
}

function createLayout(scope) {
  return {
    scope,
    shapes: new Map(),
    edges: new Map(),
    children: [],
    emitInParent: false
  };
}

function bounds(x, y, width, height) {
  return { x: Math.round(x), y: Math.round(y), width: Math.round(width), height: Math.round(height) };
}

function point(x, y) {
  return { x: Math.round(x), y: Math.round(y) };
}

function rectanglesOverlap(a, b) {
  return a.x < b.x + b.width &&
    b.x < a.x + a.width &&
    a.y < b.y + b.height &&
    b.y < a.y + a.height;
}

function integerBounds(rect) {
  return {
    x: Math.round(rect.x),
    y: Math.round(rect.y),
    width: Math.round(rect.width),
    height: Math.round(rect.height)
  };
}

function normalizeLayout(layout) {
  const extents = getShapeExtents([
    ...layout.shapes.entries(),
    ...getExpandedChildShapes(layout)
  ].map(([ element, rect ]) => ({ element, rect })));

  translateLayout(layout, OUTER_MARGIN - extents.minX, OUTER_MARGIN - extents.minY);
}

function translateLayout(layout, dx, dy) {
  for (const rect of layout.shapes.values()) {
    rect.x += dx;
    rect.y += dy;
  }

  for (const points of layout.edges.values()) {
    for (const routePoint of points) {
      routePoint.x += dx;
      routePoint.y += dy;
    }
  }

  for (const child of layout.children) {
    if (child.emitInParent) {
      translateLayout(child, dx, dy);
    }
  }
}

function getExtents(layout) {
  return getShapeExtents([ ...layout.shapes.entries() ].map(([ element, rect ]) => ({ element, rect })));
}

function getParticipantContentExtents(layout) {
  return getShapeExtents([ ...layout.shapes.entries() ]
    .filter(([ element ]) => !isExteriorArtifact(element))
    .map(([ element, rect ]) => ({ element, rect })));
}

function hasParticipantContent(layout) {
  return [ ...layout.shapes.keys() ].some(element => !isExteriorArtifact(element));
}

function getShapeExtents(shapes) {
  if (!shapes.length) {
    return { minX: 0, minY: 0, maxX: 0, maxY: 0, width: 0, height: 0 };
  }

  const minX = Math.min(...shapes.map(({ rect }) => rect.x));
  const minY = Math.min(...shapes.map(({ rect }) => rect.y));
  const maxX = Math.max(...shapes.map(({ rect }) => rect.x + rect.width));
  const maxY = Math.max(...shapes.map(({ rect }) => rect.y + rect.height));

  return { minX, minY, maxX, maxY, width: maxX - minX, height: maxY - minY };
}

function getRecordExtents(records) {
  return getShapeExtents(records.map(record => ({ rect: record.bounds })));
}

function getExpandedChildShapes(layout) {
  const shapes = [];

  for (const child of layout.children) {
    if (!child.emitInParent) {
      continue;
    }

    shapes.push(...child.shapes.entries());
    shapes.push(...getExpandedChildShapes(child));
  }

  return shapes;
}

function getExpandedChildEdges(layout) {
  const edges = [];

  for (const child of layout.children) {
    if (!child.emitInParent) {
      continue;
    }

    edges.push(...child.edges.entries());
    edges.push(...getExpandedChildEdges(child));
  }

  return edges;
}

function directConnection(source, target) {
  const sourceCenter = point(source.x + source.width / 2, source.y + source.height / 2);
  const targetCenter = point(target.x + target.width / 2, target.y + target.height / 2);

  return [ sourceCenter, targetCenter ];
}

function compareScores(a, b) {
  for (let index = 0; index < a.length; index++) {
    if (a[index] !== b[index]) {
      return a[index] - b[index];
    }
  }

  return 0;
}

function routeLength(points) {
  return toSegments(points).reduce((total, [ start, end ]) => {
    return total + manhattan(start, end);
  }, 0);
}

function cleanPoints(points) {
  const cleaned = [];

  for (const candidate of points) {
    const previous = cleaned.at(-1);

    if (!previous || previous.x !== candidate.x || previous.y !== candidate.y) {
      cleaned.push(candidate);
    }

    while (cleaned.length >= 3) {
      const next = cleaned.at(-1);
      const middle = cleaned.at(-2);
      const before = cleaned.at(-3);
      const collinear =
        (before.x - middle.x) * (next.y - middle.y) ===
        (before.y - middle.y) * (next.x - middle.x);

      if (!collinear) {
        break;
      }

      cleaned.splice(-2, 1);

      if (before.x === next.x && before.y === next.y) {
        cleaned.pop();
      }
    }
  }

  return cleaned.map(candidate => point(candidate.x, candidate.y));
}

function toSegments(points) {
  return points.slice(1).map((end, index) => [ points[index], end ]);
}

function pointInRect(candidate, rect) {
  return candidate.x > rect.x && candidate.x < rect.x + rect.width &&
    candidate.y > rect.y && candidate.y < rect.y + rect.height;
}

function inset(rect, margin) {
  return {
    x: rect.x + margin,
    y: rect.y + margin,
    width: rect.width - 2 * margin,
    height: rect.height - 2 * margin
  };
}

function segmentEntersRect(a, b, rect) {
  const dx = b.x - a.x;
  const dy = b.y - a.y;
  const edgeDirections = [ -dx, dx, -dy, dy ];
  const edgeDistances = [ a.x - rect.x, rect.x + rect.width - a.x, a.y - rect.y, rect.y + rect.height - a.y ];
  let start = 0;
  let end = 1;

  for (let index = 0; index < edgeDirections.length; index++) {
    if (edgeDirections[index] === 0) {
      if (edgeDistances[index] < 0) {
        return false;
      }
      continue;
    }

    const ratio = edgeDistances[index] / edgeDirections[index];

    if (edgeDirections[index] < 0) {
      if (ratio > end) {
        return false;
      }
      start = Math.max(start, ratio);
    } else {
      if (ratio < start) {
        return false;
      }
      end = Math.min(end, ratio);
    }
  }

  return end - start > SEGMENT_INTERSECTION_EPSILON;
}

function collinearOverlap(a, b, c, d) {
  const horizontal = a.y === b.y && c.y === d.y && a.y === c.y;
  const vertical = a.x === b.x && c.x === d.x && a.x === c.x;

  if (!horizontal && !vertical) {
    return false;
  }

  const [ aStart, aEnd, cStart, cEnd ] = horizontal
    ? [ Math.min(a.x, b.x), Math.max(a.x, b.x), Math.min(c.x, d.x), Math.max(c.x, d.x) ]
    : [ Math.min(a.y, b.y), Math.max(a.y, b.y), Math.min(c.y, d.y), Math.max(c.y, d.y) ];

  return Math.min(aEnd, cEnd) - Math.max(aStart, cStart) > 0;
}

function segmentsProperlyCross(a, b, c, d) {
  const abC = segmentDirection(a, b, c);
  const abD = segmentDirection(a, b, d);
  const cdA = segmentDirection(c, d, a);
  const cdB = segmentDirection(c, d, b);

  return abC * abD < 0 && cdA * cdB < 0;
}

function segmentDirection(a, b, c) {
  return Math.sign(
    (b.x - a.x) * (c.y - a.y) -
    (b.y - a.y) * (c.x - a.x)
  );
}

function manhattan(a, b) {
  return Math.abs(a.x - b.x) + Math.abs(a.y - b.y);
}

function validateSequenceFlows(flows, recordsByElement, scope) {
  for (const flow of flows) {
    const source = flow.sourceRef;
    const target = flow.targetRef;

    if (source && target && (source.$parent !== scope || target.$parent !== scope)) {
      throw new LayoutError(
        'CROSS_SCOPE_SEQUENCE_FLOW',
        flow.id,
        'A sequence flow cannot cross a containment scope.',
        [ source.id, target.id ]
      );
    }

    if (!source || !target || !recordsByElement.has(source) || !recordsByElement.has(target)) {
      throw new LayoutError(
        'INVALID_SEQUENCE_FLOW_ENDPOINT',
        flow.id,
        'A sequence flow must reference source and target flow nodes in its scope.'
      );
    }

  }
}

function validateMessageFlows(flows) {
  for (const flow of flows) {
    if (!flow.sourceRef || !flow.targetRef) {
      throw new LayoutError(
        'INVALID_MESSAGE_FLOW_ENDPOINT',
        flow.id,
        'A message flow must reference source and target interaction nodes.'
      );
    }
  }
}

function validateBoundaryEvents(records, recordsByElement, scope) {
  for (const record of records.filter(record => record.isBoundary)) {
    const host = record.element.attachedToRef;

    if (!host || !recordsByElement.has(host) || host.$parent !== scope) {
      throw new LayoutError(
        'INVALID_BOUNDARY_HOST',
        record.element.id,
        'A boundary event must attach to an activity in the same scope.',
        host?.id ? [ host.id ] : []
      );
    }
  }
}

function validateLinks(records, scope) {
  const events = records.filter(record => is(record.element, 'bpmn:IntermediateThrowEvent') ||
    is(record.element, 'bpmn:IntermediateCatchEvent'));
  const links = new Map();

  for (const record of events) {
    const definition = (record.element.eventDefinitions || []).find(candidate => {
      return is(candidate, 'bpmn:LinkEventDefinition');
    });

    if (!definition) {
      continue;
    }

    const name = definition.name || '';

    if (!links.has(name)) {
      links.set(name, []);
    }

    links.get(name).push(record.element);
  }

  for (const [ name, elements ] of links) {
    const throws = elements.filter(element => is(element, 'bpmn:IntermediateThrowEvent'));
    const catches = elements.filter(element => is(element, 'bpmn:IntermediateCatchEvent'));

    if (throws.length !== 1 || catches.length !== 1) {
      throw new LayoutError(
        'INVALID_LINK_EVENT_PAIR',
        elements[0].id,
        `Link event "${name}" must have exactly one throw and one catch in scope "${scope.id}".`,
        elements.map(element => element.id)
      );
    }
  }
}

function validateInputVisuals(definitions, root) {
  const diagram = (definitions.diagrams || []).find(candidate => candidate.plane?.bpmnElement === root);

  for (const di of diagram?.plane?.planeElement || []) {
    if (!di.$instanceOf('bpmndi:BPMNShape')) {
      continue;
    }

    const element = di.bpmnElement;

    if (!element || is(element, 'bpmn:Group') || (getDefaultSize(element) && isSupportedVisualElement(element))) {
      continue;
    }

    throw new LayoutError(
      'UNSUPPORTED_ELEMENT',
      element.id,
      `Cannot generate DI for visual BPMN element "${element.$type}".`
    );
  }
}

function validateParseWarnings(warnings, xml) {
  const invalidProcessRef = warnings.find(warning => warning.property === 'bpmn:processRef');

  if (invalidProcessRef) {
    throw new LayoutError(
      'INVALID_PARTICIPANT_PROCESS_REFERENCE',
      invalidProcessRef.element.id,
      'A participant processRef must reference a declared process.'
    );
  }

  const unknownType = warnings.find(warning => warning.message.includes('unknown type'));

  if (unknownType) {
    const elementId = /<bpmn:[^ >]+[^>]*\sid="([^"]+)"/.exec(xml)?.[1] || 'unknown';

    throw new LayoutError(
      'UNSUPPORTED_ELEMENT',
      elementId,
      'Cannot generate DI for an unknown BPMN visual element.'
    );
  }
}

const EDGE_PRIORITY = {
  SPINE: 0,
  STRAIGHT: 1,
  CROSS_BAND_GATEWAY_BRANCH: 2,
  STANDARD: 3,
  BACK_EDGE: 4
};

function createSemanticPolicy(scope, records, graphEdges, boundaryEdges, allRecords) {
  const edgeOrder = new Map();
  const flowNodeDocumentIndex = new Map(records.map(record => [ record.element, record.index ]));
  const allElementsDocumentIndex = new Map(allRecords.map(record => [ record.element, record.index ]));
  const outgoing = new Map(records.map(record => [ record.element, [] ]));

  graphEdges.forEach((edge, edgeIndex) => {
    edgeOrder.set(edge, edgeIndex);
    outgoing.get(edge.sourceRef).push(edge);
  });
  boundaryEdges.forEach((edge, edgeIndex) => edgeOrder.set(edge, graphEdges.length + edgeIndex));

  const compactFlowRegions = is(scope, 'bpmn:AdHocSubProcess')
    ? findCompactFlowRegions(records, graphEdges, outgoing, edgeOrder)
    : [];
  const rankWeights = createCompactRankWeights(compactFlowRegions);
  const cycleOutgoing = new Map(records.map(record => [ record.element, [] ]));

  for (const edge of graphEdges) {
    cycleOutgoing.get(edge.sourceRef).push(edge);
  }
  for (const edge of boundaryEdges) {
    cycleOutgoing.get(edge.sourceRef.attachedToRef).push(edge);
  }

  const backEdges = new Set();
  const boundaryBayEdges = new Set();

  markBackEdges(
    records.map(record => record.element),
    cycleOutgoing,
    backEdges,
    flowNodeDocumentIndex
  );
  const selectEdge = (node, candidates, visited) => {
    return selectPrimaryEdge(node, candidates, edgeOrder, visited, outgoing);
  };
  const spine = new Set();
  const starts = records.filter(record => is(record.element, 'bpmn:StartEvent'))
    .sort((a, b) => a.index - b.index);
  const incomingNodes = new Set(graphEdges.map(edge => edge.targetRef));
  const sourceNodes = records.filter(record => !incomingNodes.has(record.element))
    .map(record => record.element);
  const primarySeed = starts[0]?.element ||
    sourceNodes[0] ||
    records[0]?.element;
  const adHocSources = is(scope, 'bpmn:AdHocSubProcess') ? sourceNodes : [];
  const seeds = [
    primarySeed,
    ...adHocSources,
    ...records.filter(record => {
      return is(record.element, 'bpmn:IntermediateCatchEvent') &&
        (record.element.eventDefinitions || []).some(definition => {
          return is(definition, 'bpmn:LinkEventDefinition');
        });
    }).map(record => record.element)
  ];
  const visited = new Set();
  const claimed = new Set();
  const components = new Map();
  const adjacent = new Map(records.map(record => [ record.element, [] ]));
  let componentIndex = 0;

  for (const edge of graphEdges) {
    adjacent.get(edge.sourceRef).push(edge.targetRef);
    adjacent.get(edge.targetRef).push(edge.sourceRef);
  }
  for (const edge of boundaryEdges) {
    const host = edge.sourceRef.attachedToRef;

    adjacent.get(host).push(edge.targetRef);
    adjacent.get(edge.targetRef).push(host);
  }

  for (const seed of records.map(record => record.element)) {
    if (claimed.has(seed)) {
      continue;
    }

    const componentQueue = [ seed ];
    claimed.add(seed);
    components.set(seed, componentIndex);

    while (componentQueue.length) {
      const element = componentQueue.shift();

      for (const neighbor of adjacent.get(element)) {
        if (!claimed.has(neighbor)) {
          claimed.add(neighbor);
          components.set(neighbor, componentIndex);
          componentQueue.push(neighbor);
        }
      }
    }

    componentIndex++;
  }

  const mainComponent = components.get(primarySeed);
  const componentSeeds = new Map();

  for (const record of starts) {
    const component = components.get(record.element);

    if (!componentSeeds.has(component)) {
      componentSeeds.set(component, record.element);
    }
  }

  seeds.push(...componentSeeds.values());

  for (const seed of seeds) {
    if (!seed || visited.has(seed)) {
      continue;
    }

    if (!is(scope, 'bpmn:AdHocSubProcess') &&
        seed !== primarySeed && components.get(seed) === mainComponent) {
      continue;
    }

    let current = seed;

    while (current && !visited.has(current)) {
      visited.add(current);
      const candidates = outgoing.get(current) || [];
      const next = selectEdge(current, candidates, visited);

      if (!next) {
        break;
      }

      spine.add(next);
      current = next.targetRef;
    }
  }

  const straightEdges = new Set(spine);

  for (const record of records) {
    const candidates = outgoing.get(record.element) || [];

    if (is(record.element, 'bpmn:Gateway') && candidates.length > 1) {
      straightEdges.add(selectEdge(record.element, candidates, new Set()));
    }
  }

  const straightTargets = [ ...straightEdges ].map(edge => edge.targetRef);

  while (straightTargets.length) {
    const node = straightTargets.shift();
    const candidates = outgoing.get(node) || [];

    if (candidates.length !== 1 || straightEdges.has(candidates[0])) {
      continue;
    }

    straightEdges.add(candidates[0]);
    straightTargets.push(candidates[0].targetRef);
  }

  const bands = assignSemanticBands(
    records,
    graphEdges,
    boundaryEdges,
    straightEdges,
    flowNodeDocumentIndex,
    allElementsDocumentIndex,
    edgeOrder,
    components,
    backEdges
  );

  for (const edge of graphEdges) {
    const sourceIsJoin = is(edge.sourceRef, 'bpmn:Gateway') &&
      (edge.sourceRef.incoming || []).length > 1 &&
      (edge.sourceRef.outgoing || []).length === 1;
    const targetIsJoin = is(edge.targetRef, 'bpmn:Gateway') &&
      (edge.targetRef.incoming || []).length > 1;

    if (sourceIsJoin && targetIsJoin &&
        edge.sourceRef.$type === edge.targetRef.$type &&
        bands.get(edge.sourceRef) !== bands.get(edge.targetRef)) {
      rankWeights.set(edge, 0);
    }
  }

  alignLinkEventContinuationBands(records, graphEdges, bands);

  return {
    spine,
    straightEdges,
    bands,
    components,
    edgeOrder,
    flowNodeDocumentIndex,
    graphEdges,
    compactFlowRegions,
    rankWeights,
    backEdges,
    boundaryBayEdges
  };
}

function findCompactFlowRegions(records, graphEdges, outgoing, edgeOrder) {
  const incomingCount = new Map(records.map(record => [ record.element, 0 ]));

  for (const edge of graphEdges) {
    incomingCount.set(edge.targetRef, incomingCount.get(edge.targetRef) + 1);
  }

  const regions = [];

  for (const record of records) {
    const split = record.element;
    const branches = outgoing.get(split) || [];

    if (branches.length < 2) {
      continue;
    }

    const distances = branches.map(branch => {
      return descendantDistances(branch.targetRef, outgoing, split);
    });
    const common = [ ...distances[0].keys() ].filter(node => {
      return node !== split &&
        incomingCount.get(node) > 1 &&
        distances.every(distance => distance.has(node));
    });

    if (!common.length) {
      continue;
    }

    const join = common.sort((a, b) => {
      const distancesA = distances.map(distance => distance.get(a));
      const distancesB = distances.map(distance => distance.get(b));

      return Math.max(...distancesA) - Math.max(...distancesB) ||
        distancesA.reduce((sum, distance) => sum + distance, 0) -
          distancesB.reduce((sum, distance) => sum + distance, 0) ||
        indexOfNode(a, records) - indexOfNode(b, records);
    })[0];
    const paths = branches.map(branch => {
      return [
        branch,
        ...shortestFlowPath(branch.targetRef, join, outgoing, edgeOrder, split)
      ];
    });
    const primaryEdge = selectPrimaryEdge(split, branches, edgeOrder, new Set(), outgoing);
    const primaryPath = paths.find(path => path[0] === primaryEdge);

    regions.push({ split, join, paths, primaryPath });
  }

  return regions;
}

function descendantDistances(start, outgoing, blocked) {
  const distances = new Map([ [ start, 0 ] ]);
  const pending = [ start ];

  while (pending.length) {
    const node = pending.shift();

    for (const edge of outgoing.get(node) || []) {
      const target = edge.targetRef;

      if (target === blocked || distances.has(target)) {
        continue;
      }

      distances.set(target, distances.get(node) + 1);
      pending.push(target);
    }
  }

  return distances;
}

function shortestFlowPath(start, target, outgoing, edgeOrder, blocked) {
  if (start === target) {
    return [];
  }

  const pending = [ start ];
  const previous = new Map();
  const visited = new Set([ start, blocked ]);

  while (pending.length) {
    const node = pending.shift();
    const edges = [ ...(outgoing.get(node) || []) ]
      .sort((a, b) => edgeOrder.get(a) - edgeOrder.get(b));

    for (const edge of edges) {
      if (visited.has(edge.targetRef)) {
        continue;
      }

      visited.add(edge.targetRef);
      previous.set(edge.targetRef, edge);

      if (edge.targetRef === target) {
        const path = [];

        for (let current = target; current !== start;) {
          const previousEdge = previous.get(current);

          path.unshift(previousEdge);
          current = previousEdge.sourceRef;
        }

        return path;
      }

      pending.push(edge.targetRef);
    }
  }

  return [];
}

function indexOfNode(node, records) {
  return records.find(record => record.element === node)?.index ?? Infinity;
}

function createCompactRankWeights(regions) {
  const weights = new Map();

  for (const { paths, primaryPath } of regions) {
    const span = primaryPath.length;

    for (const path of paths) {
      if (path === primaryPath) {
        continue;
      }

      const internalCount = path.length - 1;
      const offsets = [ 0 ];

      for (let index = 1; index <= internalCount; index++) {
        const offset = internalCount === 1
          ? Math.floor(span / 2)
          : Math.floor((index - 1) * span / (internalCount - 1));

        offsets.push(offset);
      }
      offsets.push(span);

      path.forEach((edge, index) => {
        const weight = offsets[index + 1] - offsets[index];
        const existing = weights.get(edge);

        weights.set(edge, existing === undefined ? weight : Math.min(existing, weight));
      });
    }
  }

  return weights;
}

function alignLinkEventContinuationBands(records, graphEdges, bands) {
  const links = new Map();
  const outgoing = new Map(records.map(record => [ record.element, [] ]));
  const incomingCount = new Map(records.map(record => [ record.element, 0 ]));

  for (const edge of graphEdges) {
    outgoing.get(edge.sourceRef).push(edge);
    incomingCount.set(edge.targetRef, incomingCount.get(edge.targetRef) + 1);
  }

  for (const record of records) {
    const definition = (record.element.eventDefinitions || []).find(candidate => {
      return is(candidate, 'bpmn:LinkEventDefinition');
    });

    if (!definition) {
      continue;
    }

    const name = definition.name || '';

    if (!links.has(name)) {
      links.set(name, {});
    }

    if (is(record.element, 'bpmn:IntermediateThrowEvent')) {
      links.get(name).throwEvent = record.element;
    } else if (is(record.element, 'bpmn:IntermediateCatchEvent')) {
      links.get(name).catchEvent = record.element;
    }
  }

  for (const { throwEvent, catchEvent } of links.values()) {
    if (!throwEvent || !catchEvent) {
      continue;
    }

    const offset = (bands.get(throwEvent) || 0) - (bands.get(catchEvent) || 0);
    const pending = [ catchEvent ];
    const visited = new Set();

    while (pending.length) {
      const element = pending.shift();

      if (visited.has(element)) {
        continue;
      }

      visited.add(element);
      bands.set(element, (bands.get(element) || 0) + offset);

      for (const edge of outgoing.get(element) || []) {
        if (incomingCount.get(edge.targetRef) <= 1) {
          pending.push(edge.targetRef);
        }
      }
    }
  }
}

function assignBoundaryBandOffsets(boundaryEdges, flowNodeDocumentIndex) {
  const edgesByHost = new Map();

  for (const edge of boundaryEdges) {
    const host = edge.sourceRef.attachedToRef;
    const side = hasEventDefinition(edge.sourceRef, 'bpmn:EscalationEventDefinition')
      ? 'top'
      : 'bottom';

    if (!edgesByHost.has(host)) {
      edgesByHost.set(host, { top: new Map(), bottom: new Map() });
    }

    const edgesByEvent = edgesByHost.get(host)[side];

    if (!edgesByEvent.has(edge.sourceRef)) {
      edgesByEvent.set(edge.sourceRef, []);
    }

    edgesByEvent.get(edge.sourceRef).push(edge);
  }

  const assigned = new Map();

  for (const [ , sides ] of edgesByHost) {
    for (const [ side, edgesByEvent ] of Object.entries(sides)) {
      let offset = 1;
      const direction = side === 'top' ? -1 : 1;
      const eventGroups = [ ...edgesByEvent.entries() ]
        .sort(([ eventA ], [ eventB ]) => flowNodeDocumentIndex.get(eventB) - flowNodeDocumentIndex.get(eventA))
        .map(([ , edges ]) => edges);

      for (const edges of eventGroups) {
        for (const edge of edges) {
          assigned.set(edge, direction * offset);
          offset++;
        }
      }
    }
  }

  return assigned;
}

function assignSemanticBands(
    records,
    graphEdges,
    boundaryEdges,
    straightEdges,
    flowNodeDocumentIndex,
    allElementsDocumentIndex,
    edgeIndex,
    components,
    backEdges) {
  const nodes = records.map(record => record.element);
  const outgoing = new Map(nodes.map(node => [ node, [] ]));
  const incomingCount = new Map(nodes.map(node => [ node, 0 ]));

  for (const edge of graphEdges) {
    outgoing.get(edge.sourceRef).push(edge);
  }

  for (const edge of graphEdges) {
    if (!backEdges.has(edge)) {
      incomingCount.set(edge.targetRef, incomingCount.get(edge.targetRef) + 1);
    }
  }

  const bands = new Map(nodes.map(node => [ node, 0 ]));
  const occupied = new Map();
  const visited = new Set();
  const reserveBand = (component, base, offset) => {
    if (!occupied.has(component)) {
      occupied.set(component, new Set([ 0 ]));
    }

    const used = occupied.get(component);
    const direction = Math.sign(offset);
    let candidate = base + offset;

    while (used.has(candidate)) {
      candidate += direction;
    }

    used.add(candidate);
    return candidate;
  };
  const visit = (node, band, component = components.get(node)) => {
    if (visited.has(node)) {
      return;
    }

    visited.add(node);
    bands.set(node, band);

    if (!occupied.has(component)) {
      occupied.set(component, new Set());
    }
    occupied.get(component).add(band);

    const candidates = (outgoing.get(node) || [])
      .filter(edge => !backEdges.has(edge))
      .sort((a, b) => edgeIndex.get(a) - edgeIndex.get(b));
    const primary = candidates.find(edge => straightEdges.has(edge)) || candidates[0];

    if (primary) {
      visit(primary.targetRef, band, component);
    }

    let branchIndex = 0;

    for (const edge of candidates) {
      if (edge === primary) {
        continue;
      }

      const oneSided = Boolean(node.default);
      const outwardDirection = oneSided && band !== 0 ? Math.sign(band) : 1;
      const offset = branchOffset(branchIndex++, oneSided) * outwardDirection;

      visit(edge.targetRef, reserveBand(component, band, offset), component);
    }
  };
  const boundaryTargets = new Set(boundaryEdges.map(edge => edge.targetRef));
  const sources = records
    .filter(record => incomingCount.get(record.element) === 0 && !boundaryTargets.has(record.element))
    .sort((a, b) => a.index - b.index);

  for (const record of sources) {
    visit(record.element, 0);
  }

  const boundaryOffsets = assignBoundaryBandOffsets(boundaryEdges, allElementsDocumentIndex);

  for (const edge of [ ...boundaryEdges ].sort((a, b) => edgeIndex.get(a) - edgeIndex.get(b))) {
    if (visited.has(edge.targetRef)) {
      continue;
    }

    const host = edge.sourceRef.attachedToRef;
    const component = components.get(host);
    const hostBand = bands.get(host) || 0;

    visit(
      edge.targetRef,
      reserveBand(component, hostBand, boundaryOffsets.get(edge)),
      component
    );
  }

  for (const record of [ ...records ].sort((a, b) => a.index - b.index)) {
    visit(record.element, 0);
  }

  return bands;
}

function selectPrimaryEdge(
    node,
    edges,
    edgeOrder,
    visited = new Set(),
    outgoing = new Map()) {
  if (!edges.length) {
    return null;
  }

  const defaultEdge = node.default;
  const forwardEdges = edges.filter(edge => !visited.has(edge.targetRef));
  const candidates = forwardEdges.length ? forwardEdges : edges;
  const endReaching = candidates.filter(edge => {
    return canReachEndEvent(edge.targetRef, outgoing, new Set([ node ]));
  });
  const preferred = endReaching.length ? endReaching : candidates;

  return preferred.find(edge => edge === defaultEdge) ||
    [ ...preferred ].sort((a, b) => edgeOrder.get(a) - edgeOrder.get(b))[0];
}

function canReachEndEvent(node, outgoing, path) {
  if (is(node, 'bpmn:EndEvent')) {
    return true;
  }

  if (path.has(node)) {
    return false;
  }

  path.add(node);

  for (const edge of outgoing.get(node) || []) {
    if (canReachEndEvent(edge.targetRef, outgoing, path)) {
      path.delete(node);
      return true;
    }
  }

  path.delete(node);
  return false;
}

function branchOffset(index, oneSided = false) {
  if (oneSided) {
    return index + 1;
  }

  const distance = Math.floor(index / 2) + 1;

  return index % 2 === 0 ? distance : -distance;
}

function assignRanks(records, graphEdges, boundaryEdges, policy) {
  const rank = new Map(records.map(record => [ record.element, 0 ]));
  const outgoing = new Map(records.map(record => [ record.element, [] ]));
  const indegree = new Map(records.map(record => [ record.element, 0 ]));
  const backEdges = policy.backEdges;

  for (const edge of graphEdges) {
    outgoing.get(edge.sourceRef).push(edge);
  }

  for (const edge of graphEdges) {
    if (!backEdges.has(edge)) {
      indegree.set(edge.targetRef, indegree.get(edge.targetRef) + 1);
    }
  }

  const ready = records.filter(record => indegree.get(record.element) === 0)
    .sort((a, b) => a.index - b.index);
  const processed = new Set();

  while (ready.length) {
    const record = ready.shift();
    const source = record.element;

    if (processed.has(source)) {
      continue;
    }

    processed.add(source);

    for (const edge of outgoing.get(source)) {
      if (backEdges.has(edge)) {
        continue;
      }

      const weight = policy.rankWeights.get(edge) ?? 1;

      rank.set(
        edge.targetRef,
        Math.max(rank.get(edge.targetRef), rank.get(source) + weight)
      );
      indegree.set(edge.targetRef, indegree.get(edge.targetRef) - 1);

      if (indegree.get(edge.targetRef) === 0) {
        ready.push(records.find(candidate => candidate.element === edge.targetRef));
        ready.sort((a, b) => a.index - b.index);
      }
    }
  }

  for (const record of records) {
    if (!processed.has(record.element)) {
      rank.set(record.element, 0);
    }
  }

  reserveGatewayBranchSpans(rank, outgoing, policy.spine, backEdges);
  stabilizeRanks(rank, graphEdges, boundaryEdges, policy, records.length);

  // Each changing pass advances at least one spine continuation; there can be
  // no more dependent reservations than spine edges.
  for (
    let iteration = 0;
    !policy.compactFlowRegions.length && iteration < policy.spine.size;
    iteration++
  ) {
    const changed = reserveDetachedBranchSpans(
      rank,
      outgoing,
      boundaryEdges,
      policy.spine,
      backEdges,
      records,
      policy
    );

    if (!changed) {
      break;
    }

    stabilizeRanks(rank, graphEdges, boundaryEdges, policy, records.length);
  }

  return { rank, backEdges };
}

function reserveGatewayBranchSpans(rank, outgoing, spine, backEdges) {
  const spineNodes = new Set();

  for (const edge of spine) {
    spineNodes.add(edge.sourceRef);
    spineNodes.add(edge.targetRef);
  }

  for (const spineEdge of spine) {
    if (!is(spineEdge.targetRef, 'bpmn:Gateway')) {
      continue;
    }

    const branches = (outgoing.get(spineEdge.sourceRef) || []).filter(edge => {
      return edge !== spineEdge && !backEdges.has(edge);
    });
    let reservedUntil = rank.get(spineEdge.targetRef);

    for (const branch of branches) {
      const branchEnd = findDetachedBranchEnd(
        branch,
        rank,
        outgoing,
        spineNodes,
        backEdges
      );

      if (branchEnd !== null) {
        reservedUntil = Math.max(reservedUntil, branchEnd + 1);
      }
    }

    rank.set(spineEdge.targetRef, reservedUntil);
  }
}

function stabilizeRanks(rank, graphEdges, boundaryEdges, policy, maxIterations) {
  const backEdges = policy.backEdges;

  // Boundary handlers enter the normal graph after their host rank is known.
  // Resolve them together with normal flow so downstream ranks follow any
  // horizontal space reserved for detached alternatives.
  for (let iteration = 0; iteration < maxIterations; iteration++) {
    let changed = false;

    for (const edge of graphEdges) {
      if (backEdges.has(edge)) {
        continue;
      }

      const candidate = rank.get(edge.sourceRef) + (policy.rankWeights.get(edge) ?? 1);

      if (candidate > rank.get(edge.targetRef)) {
        rank.set(edge.targetRef, candidate);
        changed = true;
      }

    }

    for (const edge of boundaryEdges) {
      if (backEdges.has(edge)) {
        continue;
      }

      const hostRank = rank.get(edge.sourceRef.attachedToRef);
      const candidate = hostRank + 1;

      if (candidate > rank.get(edge.targetRef)) {
        rank.set(edge.targetRef, candidate);
        changed = true;
      }
    }

    if (!changed) {
      break;
    }
  }
}

function reserveDetachedBranchSpans(
    rank,
    outgoing,
    boundaryEdges,
    spine,
    backEdges,
    records,
    policy) {
  const spineNodes = new Set();
  const boundaryBranches = new Map();
  const laneByNode = getLaneMemberships(records);
  let changed = false;

  for (const edge of spine) {
    spineNodes.add(edge.sourceRef);
    spineNodes.add(edge.targetRef);
  }

  for (const edge of boundaryEdges) {
    const host = edge.sourceRef.attachedToRef;

    if (!boundaryBranches.has(host)) {
      boundaryBranches.set(host, []);
    }
    boundaryBranches.get(host).push(edge);
  }

  for (const spineEdge of spine) {
    const sourceBoundaryBranches = (boundaryBranches.get(spineEdge.sourceRef) || [])
      .filter(edge => !backEdges.has(edge));
    const continuationBranches = [
      ...(outgoing.get(spineEdge.targetRef) || []),
      ...(boundaryBranches.get(spineEdge.targetRef) || [])
    ].filter(edge => !backEdges.has(edge));
    const targetIsJoin = (spineEdge.targetRef.incoming || []).length > 1;
    const reserveBoundaryBay = sourceBoundaryBranches.length && targetIsJoin;
    const boundaryBayOnly = continuationBranches.length < 2;

    if (continuationBranches.length < 2 && !reserveBoundaryBay) {
      continue;
    }

    const branches = boundaryBayOnly
      ? sourceBoundaryBranches
      : [
        ...(outgoing.get(spineEdge.sourceRef) || []).filter(edge => edge !== spineEdge),
        ...sourceBoundaryBranches
      ].filter(edge => !backEdges.has(edge));
    const sourceRank = rank.get(spineEdge.sourceRef);
    let reservedUntil = rank.get(spineEdge.targetRef);

    for (const branch of branches) {
      const branchEnd = findDetachedBranchEnd(
        branch,
        rank,
        outgoing,
        spineNodes,
        backEdges,
        sourceRank,
        boundaryBayOnly ? laneByNode : null
      );

      if (branchEnd !== null) {
        if (boundaryBayOnly) {
          policy.boundaryBayEdges.add(branch);
        }

        reservedUntil = Math.max(reservedUntil, branchEnd + 1);
      }
    }

    if (reservedUntil > rank.get(spineEdge.targetRef)) {
      rank.set(spineEdge.targetRef, reservedUntil);
      changed = true;
    }
  }

  return changed;
}

function getLaneMemberships(records) {
  const laneByNode = new Map();
  const scopes = new Set(records.map(record => record.element.$parent).filter(Boolean));
  const visitLane = lane => {
    for (const node of lane.flowNodeRef || []) {
      laneByNode.set(node, lane);
    }
    for (const child of lane.childLaneSet?.lanes || []) {
      visitLane(child);
    }
  };

  for (const scope of scopes) {
    for (const laneSet of scope.laneSets || []) {
      for (const lane of laneSet.lanes || []) {
        visitLane(lane);
      }
    }
  }

  return laneByNode;
}

function findDetachedBranchEnd(
    branch,
    rank,
    outgoing,
    spineNodes,
    backEdges,
    sourceRank = null,
    laneByNode = null) {
  const pending = [ { node: branch.targetRef, distance: 1 } ];
  const visited = new Set();
  let detached = true;
  let cyclic = false;
  let lane;
  let laneInitialized = false;
  let branchEnd = sourceRank === null
    ? rank.get(branch.targetRef)
    : sourceRank + 1;

  while (pending.length) {
    const { node, distance } = pending.shift();

    if (visited.has(node)) {
      continue;
    }

    visited.add(node);
    branchEnd = Math.max(branchEnd, rank.get(node));

    if (laneByNode) {
      const nodeLane = laneByNode.get(node) || null;

      if (laneInitialized && nodeLane !== lane) {
        detached = false;
      } else {
        lane = nodeLane;
        laneInitialized = true;
      }
    }

    if (sourceRank !== null) {
      branchEnd = Math.max(branchEnd, sourceRank + distance);
    }

    if (spineNodes.has(node)) {
      detached = false;
      continue;
    }

    for (const edge of outgoing.get(node) || []) {
      if (backEdges.has(edge)) {
        cyclic = true;
      } else {
        pending.push({
          node: edge.targetRef,
          distance: distance + 1
        });
      }
    }
  }

  return detached && !cyclic ? branchEnd : null;
}

function markBackEdges(nodes, outgoing, backEdges, flowNodeDocumentIndex) {
  const state = new Map();
  const incomingCount = new Map(nodes.map(node => [ node, 0 ]));

  for (const edges of outgoing.values()) {
    for (const edge of edges) {
      incomingCount.set(edge.targetRef, incomingCount.get(edge.targetRef) + 1);
    }
  }

  function visit(node) {
    state.set(node, 'visiting');
    const edges = [ ...(outgoing.get(node) || []) ].sort((a, b) => flowNodeDocumentIndex.get(a.targetRef) - flowNodeDocumentIndex.get(b.targetRef));

    for (const edge of edges) {
      const targetState = state.get(edge.targetRef);

      if (targetState === 'visiting') {
        backEdges.add(edge);
      } else if (!targetState) {
        visit(edge.targetRef);
      }
    }

    state.set(node, 'visited');
  }

  const ordered = [ ...nodes ].sort((a, b) => {
    const sourceA = incomingCount.get(a) === 0;
    const sourceB = incomingCount.get(b) === 0;

    if (sourceA !== sourceB) {
      return sourceA ? -1 : 1;
    }

    const startA = is(a, 'bpmn:StartEvent');
    const startB = is(b, 'bpmn:StartEvent');

    if (startA !== startB) {
      return startA ? -1 : 1;
    }

    return flowNodeDocumentIndex.get(a) - flowNodeDocumentIndex.get(b);
  });

  for (const node of ordered) {
    if (!state.has(node)) {
      visit(node);
    }
  }
}

function edgePriority(edge, policy) {
  if (policy.spine?.has(edge)) {
    return EDGE_PRIORITY.SPINE;
  }

  if (policy.straightEdges?.has(edge)) {
    return EDGE_PRIORITY.STRAIGHT;
  }

  const sourceBand = policy.bands?.get(edge.sourceRef) || 0;
  const targetBand = policy.bands?.get(edge.targetRef) || 0;
  const crossBandGatewayBranch = is(edge.sourceRef, 'bpmn:Gateway') &&
    (edge.sourceRef.outgoing || []).length > 1 &&
    sourceBand !== targetBand;

  if (!policy.backEdges?.has(edge) && crossBandGatewayBranch) {
    return EDGE_PRIORITY.CROSS_BAND_GATEWAY_BRANCH;
  }

  if (policy.backEdges?.has(edge)) {
    return EDGE_PRIORITY.BACK_EDGE;
  }

  return EDGE_PRIORITY.STANDARD;
}

function placeRecords(records, ranks, policy) {
  const byRank = new Map();

  for (const record of records) {
    const rank = ranks.rank.get(record.element) || 0;
    record.rank = rank;

    if (!byRank.has(rank)) {
      byRank.set(rank, []);
    }

    byRank.get(rank).push(record);
  }

  const rankNumbers = [ ...byRank.keys() ].sort((a, b) => a - b);
  const rankWidths = new Map();
  let x = 0;

  for (const rank of rankNumbers) {
    const width = Math.max(...byRank.get(rank).map(record => record.size.width));
    rankWidths.set(rank, width);

    for (const record of byRank.get(rank)) {
      record.bounds = bounds(
        x + Math.round((width - record.size.width) / 2),
        (policy.bands.get(record.element) || 0) * (VERTICAL_GAP + SEMANTIC_BAND_HEIGHT) - record.size.height / 2,
        record.size.width,
        record.size.height
      );
    }

    x += width + HORIZONTAL_GAP;
  }

  for (const rank of rankNumbers) {
    const occupied = new Map();

    for (const record of byRank.get(rank).sort((a, b) => a.index - b.index)) {
      const band = policy.bands.get(record.element) || 0;
      const key = `${policy.components.get(record.element)}:${band}`;
      const offset = occupied.get(key) || 0;
      record.bounds.y += offset;
      occupied.set(key, offset + record.size.height + VERTICAL_GAP);
    }
  }

}

function clearBoundaryHandlerExits(records, boundaryEdges, recordsByElement, policy) {
  const ordered = [ ...boundaryEdges ].sort((a, b) => {
    const bandA = policy.bands.get(a.targetRef) || 0;
    const bandB = policy.bands.get(b.targetRef) || 0;

    return Math.abs(bandA) - Math.abs(bandB);
  });

  for (const edge of ordered) {
    const boundary = recordsByElement.get(edge.sourceRef);
    const host = recordsByElement.get(edge.sourceRef.attachedToRef);
    const target = recordsByElement.get(edge.targetRef);
    const targetBand = policy.bands.get(edge.targetRef) || 0;
    const hostBand = policy.bands.get(edge.sourceRef.attachedToRef) || 0;
    const component = policy.components.get(edge.targetRef);

    if (targetBand === hostBand) {
      continue;
    }

    const exitsTop = hasEventDefinition(
      edge.sourceRef,
      'bpmn:EscalationEventDefinition'
    );
    const boundaryExitY = exitsTop
      ? host.bounds.y - boundary.size.height / 2
      : host.bounds.y + host.bounds.height + boundary.size.height / 2;
    const targetCenterY = target.bounds.y + target.bounds.height / 2;
    const requiredCenterY = boundaryExitY +
      (exitsTop ? -ROUTING_MARGIN : ROUTING_MARGIN);
    const shift = exitsTop
      ? Math.min(0, requiredCenterY - targetCenterY)
      : Math.max(0, requiredCenterY - targetCenterY);

    if (!shift) {
      continue;
    }

    for (const record of records) {
      const band = policy.bands.get(record.element) || 0;
      const sameSideOrFurther = exitsTop
        ? band <= targetBand
        : band >= targetBand;

      if (
        policy.components.get(record.element) === component &&
        sameSideOrFurther
      ) {
        record.bounds.y += shift;
      }
    }
  }
}

function compactSemanticBands(records, graphEdges, boundaryEdges, ranks, policy) {
  const intervals = new Map();
  const outgoingCount = new Map(records.map(record => [ record.element, 0 ]));
  const addInterval = (component, band, min, max, boundary = false) => {
    if (!band) {
      return;
    }

    const key = `${component}:${band}`;
    const existing = intervals.get(key);

    if (existing) {
      existing.spans.push({ min, max });
      existing.boundary ||= boundary;
    } else {
      intervals.set(key, {
        component,
        band,
        boundary,
        spans: [ { min, max } ]
      });
    }
  };

  for (const edge of graphEdges) {
    outgoingCount.set(edge.sourceRef, outgoingCount.get(edge.sourceRef) + 1);
  }

  for (const record of records) {
    const element = record.element;
    const rank = ranks.rank.get(element);

    addInterval(
      policy.components.get(element),
      policy.bands.get(element) || 0,
      rank,
      rank
    );
  }

  for (const edge of graphEdges) {
    if (policy.backEdges.has(edge)) {
      continue;
    }

    const sourceRank = ranks.rank.get(edge.sourceRef);
    const targetRank = ranks.rank.get(edge.targetRef);
    const min = Math.min(sourceRank, targetRank);
    const max = Math.max(sourceRank, targetRank);
    const sourceBand = policy.bands.get(edge.sourceRef) || 0;
    const targetBand = policy.bands.get(edge.targetRef) || 0;
    const occupiedBand = sourceBand === targetBand
      ? sourceBand
      : outgoingCount.get(edge.sourceRef) > 1
        ? targetBand
        : sourceBand;

    addInterval(
      policy.components.get(edge.sourceRef),
      occupiedBand,
      min,
      max
    );
  }

  for (const edge of boundaryEdges) {
    const host = edge.sourceRef.attachedToRef;
    const sourceRank = ranks.rank.get(host);
    const targetRank = ranks.rank.get(edge.targetRef);

    addInterval(
      policy.components.get(host),
      policy.bands.get(edge.targetRef) || 0,
      Math.min(sourceRank, targetRank),
      Math.max(sourceRank, targetRank),
      policy.boundaryBayEdges.has(edge)
    );
  }

  const assigned = new Map();
  const mapping = new Map();
  const boundaryHosts = new Map();

  for (const edge of boundaryEdges) {
    const host = edge.sourceRef.attachedToRef;
    const component = policy.components.get(host);
    const targetBand = policy.bands.get(edge.targetRef) || 0;
    const key = `${component}:${targetBand}`;

    if (!boundaryHosts.has(key)) {
      boundaryHosts.set(key, []);
    }
    boundaryHosts.get(key).push(policy.bands.get(host) || 0);
  }

  const ordered = [ ...intervals.values() ].sort((a, b) => {
    return a.component - b.component ||
      Math.sign(a.band) - Math.sign(b.band) ||
      Number(b.boundary) - Number(a.boundary) ||
      Math.abs(a.band) - Math.abs(b.band);
  });

  for (const interval of ordered) {
    const direction = Math.sign(interval.band);
    const hostBands = boundaryHosts.get(`${interval.component}:${interval.band}`) || [];
    const minimumMagnitude = hostBands.reduce((minimum, hostBand) => {
      if (Math.sign(hostBand) !== direction) {
        return minimum;
      }

      const compactedHost = mapping.get(`${interval.component}:${hostBand}`) || hostBand;

      return Math.max(minimum, Math.abs(compactedHost) + 1);
    }, 1);
    let compacted = direction * minimumMagnitude;
    let placed = false;

    while (!placed) {
      const key = `${interval.component}:${compacted}`;
      const occupied = assigned.get(key) || [];
      const overlaps = occupied.some(existing => {
        return interval.spans.some(span => {
          return existing.spans.some(other => {
            return span.min <= other.max && span.max >= other.min;
          });
        });
      });

      if (!overlaps) {
        occupied.push(interval);
        assigned.set(key, occupied);
        mapping.set(`${interval.component}:${interval.band}`, compacted);
        placed = true;
        continue;
      }

      compacted += direction;
    }
  }

  for (const record of records) {
    const element = record.element;
    const band = policy.bands.get(element) || 0;

    if (band) {
      policy.bands.set(
        element,
        mapping.get(`${policy.components.get(element)}:${band}`)
      );
    }
  }
}

function packComponents(scope, records, graphEdges, boundaryEdges) {
  const parent = new Map(records.map(record => [ record.element, record.element ]));
  const find = element => {
    const root = parent.get(element);

    if (root === element) {
      return root;
    }

    const compressed = find(root);
    parent.set(element, compressed);
    return compressed;
  };
  const union = (a, b) => {
    const rootA = find(a);
    const rootB = find(b);

    if (rootA !== rootB) {
      parent.set(rootB, rootA);
    }
  };

  for (const edge of [ ...graphEdges, ...boundaryEdges ]) {
    const source = is(edge.sourceRef, 'bpmn:BoundaryEvent')
      ? edge.sourceRef.attachedToRef
      : edge.sourceRef;

    if (parent.has(source) && parent.has(edge.targetRef)) {
      union(source, edge.targetRef);
    }
  }

  const components = new Map();

  for (const record of records) {
    const root = find(record.element);

    if (!components.has(root)) {
      components.set(root, []);
    }

    components.get(root).push(record);
  }

  const ordered = [ ...components.values() ].sort((a, b) => {
    return Math.min(...a.map(record => record.index)) - Math.min(...b.map(record => record.index));
  });

  for (const component of ordered) {
    separateRankOverlaps(component);
  }

  if (is(scope, 'bpmn:AdHocSubProcess')) {
    packCompactComponents(ordered);
    return;
  }

  let y = 0;

  for (const component of ordered) {
    const extents = getRecordExtents(component);
    const dx = -extents.minX;
    const dy = y - extents.minY;

    for (const record of component) {
      record.bounds.x += dx;
      record.bounds.y += dy;
    }

    y += extents.height + 2 * VERTICAL_GAP;
  }
}

function packCompactComponents(components) {
  const items = components.map(component => {
    const extents = getRecordExtents(component);

    return {
      component,
      extents,
      index: Math.min(...component.map(record => record.index)),
      width: extents.width,
      height: extents.height
    };
  }).sort((a, b) => {
    return b.height - a.height ||
      b.width - a.width ||
      a.index - b.index;
  });
  const totalArea = items.reduce((sum, item) => {
    return sum +
      (item.width + HORIZONTAL_GAP) *
      (item.height + VERTICAL_GAP);
  }, 0);
  const packingWidth = Math.max(
    ...items.map(item => item.width),
    Math.ceil(Math.sqrt(totalArea))
  );
  const placed = [];

  for (const item of items) {
    const xs = [ 0, ...placed.map(candidate => candidate.x + candidate.width + HORIZONTAL_GAP) ];
    const ys = [ 0, ...placed.map(candidate => candidate.y + candidate.height + VERTICAL_GAP) ];
    let placement = null;

    for (const y of [ ...new Set(ys) ].sort((a, b) => a - b)) {
      for (const x of [ ...new Set(xs) ].sort((a, b) => a - b)) {
        const candidate = bounds(x, y, item.width, item.height);

        if (x + item.width > packingWidth) {
          continue;
        }
        if (placed.some(other => rectanglesOverlapWithGap(candidate, other))) {
          continue;
        }

        placement = candidate;
        break;
      }

      if (placement) {
        break;
      }
    }

    if (!placement) {
      const y = placed.length
        ? Math.max(...placed.map(candidate => candidate.y + candidate.height)) + VERTICAL_GAP
        : 0;

      placement = bounds(0, y, item.width, item.height);
    }

    const dx = placement.x - item.extents.minX;
    const dy = placement.y - item.extents.minY;

    for (const record of item.component) {
      record.bounds.x += dx;
      record.bounds.y += dy;
    }

    placed.push(placement);
  }
}

function rectanglesOverlapWithGap(a, b) {
  return a.x < b.x + b.width + HORIZONTAL_GAP &&
    a.x + a.width + HORIZONTAL_GAP > b.x &&
    a.y < b.y + b.height + VERTICAL_GAP &&
    a.y + a.height + VERTICAL_GAP > b.y;
}

function separateRankOverlaps(records) {
  const byRank = new Map();

  for (const record of records) {
    if (!byRank.has(record.rank)) {
      byRank.set(record.rank, []);
    }

    byRank.get(record.rank).push(record);
  }

  for (const rankRecords of byRank.values()) {
    const placed = [];

    for (const record of rankRecords.sort((a, b) => a.index - b.index)) {
      let blockers;

      while ((blockers = placed.filter(other => rectanglesOverlap(record.bounds, other.bounds))).length) {
        record.bounds.y = Math.max(
          record.bounds.y,
          ...blockers.map(other => other.bounds.y + other.bounds.height + VERTICAL_GAP)
        );
      }

      placed.push(record);
    }
  }
}

function applyLaneMembership(scope, records, graphEdges, policy, layout) {
  const lanes = flattenLanes(scope.laneSets || []);

  if (!lanes.length) {
    return;
  }

  const memberships = new Map();

  for (const lane of lanes) {
    for (const node of lane.flowNodeRef || []) {
      if (!memberships.has(node)) {
        memberships.set(node, []);
      }

      memberships.get(node).push(lane);
    }
  }

  for (const [ node, nodeLanes ] of memberships) {
    const deepest = nodeLanes.filter(lane => {
      return !nodeLanes.some(other => other !== lane && laneContains(lane, other));
    });

    if (deepest.length !== 1) {
      throw new LayoutError(
        'INVALID_LANE_MEMBERSHIP',
        node.id,
        'A flow node must have one deepest lane membership.',
        deepest.map(lane => lane.id)
      );
    }

    memberships.set(node, deepest);
  }

  const maxRight = Math.max(...records.map(record => record.bounds.x + record.bounds.width), MIN_LANE_CONTENT_WIDTH);

  for (const record of records) {
    record.bounds.x += LANE_CONTENT_PADDING;
  }

  const laneHeights = new Map();
  const rowsByLane = new Map();
  const recordByElement = new Map(records.map(record => [ record.element, record ]));
  const rowParents = new Map(records.map(record => [ record, record ]));
  const rowMembers = new Map(records.map(record => [ record, [ record ] ]));
  const findRow = record => {
    let root = record;

    while (rowParents.get(root) !== root) {
      root = rowParents.get(root);
    }

    while (rowParents.get(record) !== record) {
      const parent = rowParents.get(record);

      rowParents.set(record, root);
      record = parent;
    }

    return root;
  };
  const mergeRows = (a, b) => {
    const rootA = findRow(a);
    const rootB = findRow(b);

    if (rootA === rootB) {
      return;
    }

    const membersA = rowMembers.get(rootA);
    const membersB = rowMembers.get(rootB);
    const collides = membersA.some(recordA => {
      return membersB.some(recordB => {
        return recordA.bounds.x < recordB.bounds.x + recordB.bounds.width &&
          recordA.bounds.x + recordA.bounds.width > recordB.bounds.x;
      });
    });

    if (collides) {
      return;
    }

    rowParents.set(rootB, rootA);
    rowMembers.set(rootA, [ ...membersA, ...membersB ]);
    rowMembers.delete(rootB);
  };

  for (const lane of lanes) {
    const recordsByCenter = new Map();

    for (const record of records.filter(record => memberships.get(record.element)?.[0] === lane)) {
      const centerY = record.bounds.y + record.bounds.height / 2;
      const existing = recordsByCenter.get(centerY);

      if (existing) {
        mergeRows(existing, record);
      } else {
        recordsByCenter.set(centerY, record);
      }
    }
  }

  for (const edge of graphEdges) {
    const source = recordByElement.get(edge.sourceRef);
    const target = recordByElement.get(edge.targetRef);
    const sourceLane = memberships.get(edge.sourceRef)?.[0];
    const targetLane = memberships.get(edge.targetRef)?.[0];
    const linearContinuation =
      (edge.sourceRef.outgoing || []).filter(flow => is(flow, 'bpmn:SequenceFlow')).length === 1 &&
      (edge.targetRef.incoming || []).filter(flow => is(flow, 'bpmn:SequenceFlow')).length === 1;

    if (source && target && sourceLane && sourceLane === targetLane &&
        !policy.backEdges.has(edge) &&
        (policy.straightEdges.has(edge) || linearContinuation)) {
      mergeRows(source, target);
    }
  }

  const getLaneRows = lane => {
    if (rowsByLane.has(lane)) {
      return rowsByLane.get(lane);
    }

    const rows = new Map();
    const directRecords = records.filter(record => memberships.get(record.element)?.[0] === lane);

    for (const record of directRecords) {
      const root = findRow(record);

      if (!rows.has(root)) {
        rows.set(root, []);
      }

      rows.get(root).push(record);
    }

    const ordered = [ ...rows.values() ]
      .sort((rowA, rowB) => {
        const centerA = Math.min(...rowA.map(record => record.bounds.y + record.bounds.height / 2));
        const centerB = Math.min(...rowB.map(record => record.bounds.y + record.bounds.height / 2));

        return centerA - centerB;
      });

    rowsByLane.set(lane, ordered);
    return ordered;
  };
  const requiredLaneHeight = lane => {
    if (laneHeights.has(lane)) {
      return laneHeights.get(lane);
    }

    const rows = getLaneRows(lane);
    const directHeight = rows.length
      ? rows.reduce((total, row) => {
        return total + Math.max(...row.map(record => record.bounds.height));
      }, 0) + Math.max(0, rows.length - 1) * VERTICAL_GAP + 2 * VERTICAL_GAP
      : 0;
    const childrenHeight = (lane.childLaneSet?.lanes || []).reduce((total, child) => {
      return total + requiredLaneHeight(child);
    }, 0);
    const height = Math.max(MIN_LANE_HEIGHT, directHeight, childrenHeight);

    laneHeights.set(lane, height);
    return height;
  };

  let y = 0;

  for (const lane of lanes.filter(lane => !lanes.some(other => other !== lane && laneContains(other, lane)))) {
    const height = requiredLaneHeight(lane);

    addLaneLayout(lane, y, height, maxRight + 2 * LANE_CONTENT_PADDING, layout, laneHeights);
    y += height;
  }

  for (const lane of lanes) {
    const laneBounds = layout.shapes.get(lane);
    const rows = getLaneRows(lane);

    if (!laneBounds || !rows.length) {
      continue;
    }

    const totalHeight = rows.reduce((total, row) => {
      return total + Math.max(...row.map(record => record.bounds.height));
    }, 0) + Math.max(0, rows.length - 1) * VERTICAL_GAP;
    let recordY = laneBounds.y + Math.round((laneBounds.height - totalHeight) / 2);

    for (const row of rows) {
      const rowHeight = Math.max(...row.map(record => record.bounds.height));
      const centerY = recordY + rowHeight / 2;

      for (const record of row) {
        record.bounds.y = centerY - record.bounds.height / 2;
      }

      recordY += rowHeight + VERTICAL_GAP;
    }
  }
}

function flattenLanes(laneSets) {
  const lanes = [];

  for (const laneSet of laneSets) {
    for (const lane of laneSet.lanes || []) {
      lanes.push(lane);
      lanes.push(...flattenLanes(lane.childLaneSet ? [ lane.childLaneSet ] : []));
    }
  }

  return lanes;
}

function laneContains(ancestor, candidate) {
  return flattenLanes(ancestor.childLaneSet ? [ ancestor.childLaneSet ] : []).includes(candidate);
}

function addLaneLayout(lane, y, height, width, layout, laneHeights) {
  layout.shapes.set(lane, bounds(0, y, width, height));

  const children = lane.childLaneSet?.lanes || [];

  if (!children.length) {
    return;
  }

  let childY = y;

  for (const child of children) {
    const childHeight = laneHeights.get(child);
    addLaneLayout(child, childY, childHeight, width, layout, laneHeights);
    childY += childHeight;
  }
}

function placeBoundaryEvents(records, recordsByElement) {
  const boundaries = records.filter(record => record.isBoundary);
  const byHost = new Map();

  for (const record of boundaries) {
    const host = record.element.attachedToRef;

    if (!byHost.has(host)) {
      byHost.set(host, []);
    }

    byHost.get(host).push(record);
  }

  for (const [ host, attachers ] of byHost) {
    const hostRecord = recordsByElement.get(host);
    const hostBounds = hostRecord.bounds;
    const top = attachers.filter(record => hasEventDefinition(record.element, 'bpmn:EscalationEventDefinition'));
    const bottom = attachers.filter(record => !top.includes(record));

    placeAttachers(top, hostBounds, true, recordsByElement);
    placeAttachers(bottom, hostBounds, false, recordsByElement);
  }
}

function placeAttachers(records, hostBounds, onTop, recordsByElement) {
  const outward = onTop ? -1 : 1;

  records.sort((a, b) => {
    const aDistance = boundaryHandlerDistance(a, hostBounds, outward, recordsByElement);
    const bDistance = boundaryHandlerDistance(b, hostBounds, outward, recordsByElement);

    return bDistance - aDistance || a.index - b.index;
  }).forEach((record, index) => {
    const x = Math.round(hostBounds.x + hostBounds.width / 2 +
      (index - (records.length - 1) / 2) * (record.size.width + BOUNDARY_EVENT_SPACING) - record.size.width / 2);
    const y = onTop
      ? hostBounds.y - record.size.height / 2
      : hostBounds.y + hostBounds.height - record.size.height / 2;

    record.bounds = bounds(x, y, record.size.width, record.size.height);
  });
}

function boundaryHandlerDistance(record, hostBounds, outward, recordsByElement) {
  const targets = (record.element.outgoing || [])
    .map(flow => recordsByElement.get(flow.targetRef)?.bounds)
    .filter(Boolean);

  if (!targets.length) {
    return 0;
  }

  const targetCenterY = targets.reduce((sum, target) => {
    return sum + target.y + target.height / 2;
  }, 0) / targets.length;
  const hostSideY = onHostSide(hostBounds, outward);

  return outward * (targetCenterY - hostSideY);
}

function onHostSide(hostBounds, outward) {
  return outward < 0 ? hostBounds.y : hostBounds.y + hostBounds.height;
}

// Preferred fractional x-offsets (in that order) when searching for a clear
// vertical rejoin dock along a target's width.
const CLEAR_DOCK_X_FRACTIONS = [ 0.5, 0.25, 0.75 ];

function routeConnection(flow, source, target, shapes, routedConnections, policy) {
  if (flow.sourceRef === flow.targetRef) {
    return routeSelfLoop(flow, source, target, shapes);
  }

  if (is(flow.sourceRef, 'bpmn:BoundaryEvent') &&
      flow.sourceRef.attachedToRef === flow.targetRef) {
    return routeSelfLoop(flow, source, target, shapes);
  }

  const feedback = policy.backEdges?.has(flow);
  const isBack = feedback || target.x < source.x;
  const sourceCenterX = source.x + source.width / 2;
  const targetCenterX = target.x + target.width / 2;
  const sourceCenterY = source.y + source.height / 2;
  const targetCenterY = target.y + target.height / 2;
  const sameSemanticBand =
    (policy.bands.get(flow.sourceRef) || 0) ===
    (policy.bands.get(flow.targetRef) || 0);
  const horizontalTargetDock = !isBack &&
    sameSemanticBand &&
    sourceCenterY >= target.y &&
    sourceCenterY <= target.y + target.height;
  const targetDockY = horizontalTargetDock ? sourceCenterY : targetCenterY;
  const forwardStart = point(source.x + source.width, sourceCenterY);
  const forwardEnd = point(target.x, targetDockY);
  const longForward = !isBack &&
    sourceCenterY === targetCenterY &&
    !segmentIsClear(forwardStart, forwardEnd, shapes, flow.sourceRef, flow.targetRef, []);
  const sourceBoundary = is(flow.sourceRef, 'bpmn:BoundaryEvent');
  const sourceDefinition = flow.sourceRef.eventDefinitions || [];
  const sourceTop = sourceBoundary && sourceDefinition.some(definition => is(definition, 'bpmn:EscalationEventDefinition'));
  const boundaryRejoin = sourceBoundary && [ ...(policy.spine || []) ]
    .some(edge => edge.targetRef === flow.targetRef);
  const splitBranch = !isBack && !policy.straightEdges?.has(flow) &&
    (flow.sourceRef.outgoing || []).length > 1 &&
    sourceCenterY !== targetCenterY;
  const gatewayBranch = splitBranch && is(flow.sourceRef, 'bpmn:Gateway');
  const boundaryBranch = splitBranch && sourceBoundary;
  const implicitBranch = splitBranch && !gatewayBranch && !boundaryBranch;
  const crossBand = !isBack && !sourceBoundary &&
    !horizontalTargetDock &&
    sourceCenterY !== targetCenterY;
  const verticalTarget = crossBand && !splitBranch;
  const alignedCrossBand = crossBand && sourceCenterX === targetCenterX;
  const gatewayCrossBand = !isBack &&
    is(flow.sourceRef, 'bpmn:Gateway') &&
    (flow.sourceRef.outgoing || []).length > 1 &&
    sourceCenterY !== targetCenterY;
  const gatewayTargetAbove = gatewayCrossBand && targetCenterY < sourceCenterY;
  const targetFromAbove = crossBand && sourceCenterY < targetCenterY;
  const start = sourceBoundary
    ? point(source.x + source.width / 2, sourceTop ? source.y : source.y + source.height)
    : alignedCrossBand
      ? point(sourceCenterX, targetCenterY < sourceCenterY ? source.y : source.y + source.height)
      : gatewayCrossBand
        ? point(
          source.x + source.width / 2,
          gatewayTargetAbove ? source.y : source.y + source.height
        )
        : feedback || longForward
          ? point(source.x + source.width / 2, source.y + source.height)
          : isBack
            ? point(source.x, source.y + source.height / 2)
            : point(source.x + source.width, source.y + source.height / 2);
  let end = feedback || longForward
    ? point(target.x + target.width / 2, target.y + target.height)
    : boundaryRejoin
      ? point(target.x + target.width / 2, sourceTop ? target.y : target.y + target.height)
      : verticalTarget
        ? point(target.x + target.width / 2, targetFromAbove ? target.y : target.y + target.height)
        : isBack
          ? point(target.x + target.width, target.y + target.height / 2)
          : point(target.x, targetDockY);

  if (boundaryRejoin) {
    end = findClearVerticalDock(
      target,
      sourceTop,
      shapes,
      flow.sourceRef,
      flow.targetRef
    );
  }

  if (!isBack && start.y === end.y && segmentIsClear(start, end, shapes, flow.sourceRef, flow.targetRef, routedConnections)) {
    return [ start, end ];
  }

  if (gatewayBranch || boundaryBranch) {
    const branchRoute = cleanPoints([ start, point(start.x, end.y), end ]);

    if (pathIsClear(branchRoute, shapes, flow.sourceRef, flow.targetRef, routedConnections)) {
      return branchRoute;
    }
  }

  if (implicitBranch) {
    const channelX = Math.round((start.x + end.x) / 2);
    const branchRoute = cleanPoints([
      start,
      point(channelX, start.y),
      point(channelX, end.y),
      end
    ]);

    if (pathIsClear(branchRoute, shapes, flow.sourceRef, flow.targetRef, routedConnections)) {
      return branchRoute;
    }
  }

  if (crossBand) {
    const joinRoute = cleanPoints([ start, point(end.x, start.y), end ]);

    if (pathIsClear(joinRoute, shapes, flow.sourceRef, flow.targetRef, routedConnections)) {
      return joinRoute;
    }

    const targetAbove = targetCenterY < sourceCenterY;
    const centeredEndpoints =
      (is(flow.sourceRef, 'bpmn:Event') || is(flow.sourceRef, 'bpmn:Gateway')) &&
      (is(flow.targetRef, 'bpmn:Event') || is(flow.targetRef, 'bpmn:Gateway'));

    if (centeredEndpoints) {
      const facingStart = point(
        sourceCenterX,
        targetAbove ? source.y : source.y + source.height
      );
      const facingEnd = point(
        targetCenterX,
        targetAbove ? target.y + target.height : target.y
      );

      for (let attempt = 1; attempt <= MAX_ROUTE_SEARCH_ATTEMPTS; attempt++) {
        const channelY = facingEnd.y + (targetAbove ? 1 : -1) * attempt * ROUTING_MARGIN;

        if (
          (targetAbove && channelY >= facingStart.y) ||
          (!targetAbove && channelY <= facingStart.y)
        ) {
          break;
        }

        const facingRoute = cleanPoints([
          facingStart,
          point(facingStart.x, channelY),
          point(facingEnd.x, channelY),
          facingEnd
        ]);

        if (pathIsClear(
          facingRoute,
          shapes,
          flow.sourceRef,
          flow.targetRef,
          routedConnections
        )) {
          return facingRoute;
        }
      }
    }

    const transposedStart = point(
      sourceCenterX,
      targetAbove ? source.y : source.y + source.height
    );
    const transposedEnd = point(
      sourceCenterX < targetCenterX ? target.x : target.x + target.width,
      targetCenterY
    );
    const transposedRoute = cleanPoints([
      transposedStart,
      point(transposedStart.x, transposedEnd.y),
      transposedEnd
    ]);

    if (pathIsClear(
      transposedRoute,
      shapes,
      flow.sourceRef,
      flow.targetRef,
      routedConnections
    )) {
      return transposedRoute;
    }
  }

  if (longForward || (feedback && !sourceBoundary)) {
    const localBypass = findLocalUBypass(
      flow,
      start,
      end,
      shapes,
      policy,
      routedConnections
    );

    if (localBypass) {
      return localBypass;
    }
  }

  const extents = getShapeExtents(shapes);

  if (boundaryRejoin) {
    for (let attempt = 1; attempt <= MAX_ROUTE_SEARCH_ATTEMPTS; attempt++) {
      const spacing = attempt * ROUTING_MARGIN;
      const channelY = sourceTop
        ? Math.min(start.y, end.y) - spacing
        : Math.max(start.y, end.y) + spacing;
      const rejoinRoute = cleanPoints([
        start,
        point(start.x, channelY),
        point(end.x, channelY),
        end
      ]);

      if (pathIsClear(rejoinRoute, shapes, flow.sourceRef, flow.targetRef, routedConnections)) {
        return rejoinRoute;
      }
    }
  }

  if (feedback || longForward) {
    for (let attempt = 1; attempt <= MAX_ROUTE_SEARCH_ATTEMPTS; attempt++) {
      const channelY = extents.maxY + attempt * ROUTING_MARGIN;
      const backRoute = cleanPoints([
        start,
        point(start.x, channelY),
        point(end.x, channelY),
        end
      ]);

      if (pathIsClear(backRoute, shapes, flow.sourceRef, flow.targetRef, routedConnections)) {
        return backRoute;
      }
    }
  }

  const channelY = isBack
    ? extents.maxY + ROUTING_MARGIN
    : sourceBoundary
      ? end.y
      : sourceTop
        ? Math.min(start.y, end.y) - ROUTING_MARGIN
        : Math.max(start.y, end.y) + ROUTING_MARGIN;
  const leadX = isBack
    ? Math.min(start.x, end.x) - ROUTING_MARGIN
    : Math.round((start.x + end.x) / 2);
  const preferred = cleanPoints([
    start,
    point(sourceBoundary ? start.x : leadX, start.y),
    point(sourceBoundary ? start.x : leadX, channelY),
    point(end.x, channelY),
    end
  ]);

  if (pathIsClear(preferred, shapes, flow.sourceRef, flow.targetRef, routedConnections)) {
    return preferred;
  }

  const route = visibilityRoute(start, end, shapes, flow.sourceRef, flow.targetRef, routedConnections) ||
    visibilityRoute(start, end, shapes, flow.sourceRef, flow.targetRef, []);

  if (route) {
    return route;
  }

  const outerRoute = findOuterRoute(
    start,
    end,
    shapes,
    flow.sourceRef,
    flow.targetRef,
    routedConnections,
    isBack,
    sourceBoundary,
    sourceTop
  ) || findOuterRoute(
    start,
    end,
    shapes,
    flow.sourceRef,
    flow.targetRef,
    [],
    isBack,
    sourceBoundary,
    sourceTop
  );

  if (outerRoute) {
    return outerRoute;
  }

  const perimeterRoute = findPerimeterRoute(
    source,
    target,
    shapes,
    flow.sourceRef,
    flow.targetRef,
    routedConnections
  ) || findPerimeterRoute(
    source,
    target,
    shapes,
    flow.sourceRef,
    flow.targetRef,
    []
  );

  if (!perimeterRoute) {
    throw new LayoutError(
      'ROUTING_FAILED',
      flow.id,
      `No legal orthogonal route could be found without crossing a shape (${flow.id}).`
    );
  }

  return perimeterRoute;
}

function routeSelfLoop(flow, source, target, shapes) {
  const start = point(source.x + source.width / 2, source.y + source.height);
  const end = point(target.x, target.y + target.height / 2);

  for (let attempt = 1; attempt <= MAX_ROUTE_SEARCH_ATTEMPTS; attempt++) {
    const spacing = attempt * ROUTING_MARGIN;
    const channelX = target.x - spacing;
    const channelY = Math.max(
      source.y + source.height,
      target.y + target.height
    ) + spacing;
    const candidate = [
      start,
      point(start.x, channelY),
      point(channelX, channelY),
      point(channelX, end.y),
      end
    ];

    if (pathIsClear(candidate, shapes, flow.sourceRef, flow.targetRef, [])) {
      return candidate;
    }
  }

  throw new LayoutError(
    'ROUTING_FAILED',
    flow.id,
    `No legal self-loop route could be found without crossing a shape (${flow.id}).`
  );
}

function findClearVerticalDock(target, onTop, shapes, sourceElement, targetElement) {
  const y = onTop ? target.y : target.y + target.height;
  const candidates = CLEAR_DOCK_X_FRACTIONS.map(offset => {
    return point(target.x + target.width * offset, y);
  });

  return candidates.find(candidate => {
    const outside = point(candidate.x, candidate.y + (onTop ? -ROUTING_MARGIN : ROUTING_MARGIN));

    return segmentIsClear(outside, candidate, shapes, sourceElement, targetElement, []);
  }) || candidates[0];
}

function findLocalUBypass(
    flow,
    start,
    end,
    shapes,
    policy,
    routedConnections) {
  const sourceElement = flow.sourceRef;
  const targetElement = flow.targetRef;
  const shapeByElement = new Map(shapes.map(({ element, rect }) => [ element, rect ]));
  const source = shapeByElement.get(sourceElement);
  const target = shapeByElement.get(targetElement);
  const centerY = source.y + source.height / 2;
  const directStart = source.x < target.x
    ? point(source.x + source.width, centerY)
    : point(source.x, centerY);
  const directEnd = source.x < target.x
    ? point(target.x, centerY)
    : point(target.x + target.width, centerY);
  const blockers = shapes.filter(({ element, rect }) => {
    return element !== sourceElement &&
      element !== targetElement &&
      segmentEntersRect(directStart, directEnd, inset(rect, ROUTE_OBSTACLE_INSET));
  });
  const nearestBottom = Math.max(
    source.y + source.height,
    target.y + target.height,
    ...blockers.map(({ rect }) => rect.y + rect.height)
  );
  const depth = uShapeDepth(flow, shapes, policy);
  const isolated = !hasOverlappingUShape(flow, shapes, policy);
  const balancedDefault = sourceElement.default === flow && isolated;
  const nearestTop = Math.min(
    source.y,
    target.y,
    ...blockers.map(({ rect }) => rect.y)
  );
  const topStart = point(source.x + source.width / 2, source.y);
  const topEnd = point(target.x + target.width / 2, target.y);

  if (!balancedDefault) {
    return findClearLocalUChannel(
      start,
      end,
      nearestBottom,
      1,
      depth,
      shapes,
      sourceElement,
      targetElement,
      routedConnections
    ) || findClearLocalUChannel(
      start,
      end,
      nearestBottom,
      1,
      depth,
      shapes,
      sourceElement,
      targetElement,
      []
    ) || findClearLocalUChannel(
      topStart,
      topEnd,
      nearestTop,
      -1,
      depth,
      shapes,
      sourceElement,
      targetElement,
      routedConnections
    ) || findClearLocalUChannel(
      topStart,
      topEnd,
      nearestTop,
      -1,
      depth,
      shapes,
      sourceElement,
      targetElement,
      []
    );
  }

  for (const connections of [ routedConnections, [] ]) {
    const bottom = findClearLocalUChannel(
      start,
      end,
      nearestBottom,
      1,
      depth,
      shapes,
      sourceElement,
      targetElement,
      connections
    );
    const top = findClearLocalUChannel(
      topStart,
      topEnd,
      nearestTop,
      -1,
      depth,
      shapes,
      sourceElement,
      targetElement,
      connections
    );
    const candidates = [ top, bottom ].filter(Boolean);

    if (candidates.length) {
      return candidates.sort((a, b) => routeLength(a) - routeLength(b))[0];
    }
  }

  return null;
}

function findClearLocalUChannel(
    start,
    end,
    nearest,
    direction,
    depth,
    shapes,
    sourceElement,
    targetElement,
    routedConnections) {
  for (let attempt = 0; attempt < MAX_LOCAL_U_CHANNEL_ATTEMPTS; attempt++) {
    const channelY = nearest + direction * (depth + attempt) * ROUTING_MARGIN;
    const candidate = cleanPoints([
      start,
      point(start.x, channelY),
      point(end.x, channelY),
      end
    ]);

    if (pathIsClear(
      candidate,
      shapes,
      sourceElement,
      targetElement,
      routedConnections,
      -3
    )) {
      return candidate;
    }
  }

  return null;
}

function uShapeDepth(flow, shapes, policy) {
  const shapeByElement = new Map(shapes.map(({ element, rect }) => [ element, rect ]));
  const candidates = uShapeCandidates(shapes, policy, shapeByElement);
  const memo = new Map();
  const depth = edge => {
    if (memo.has(edge)) {
      return memo.get(edge);
    }

    const [ left, right ] = uShapeSpan(edge, shapeByElement);
    const nested = candidates.filter(other => {
      if (other === edge) {
        return false;
      }

      const otherSource = shapeByElement.get(other.sourceRef);
      const [ otherLeft ] = uShapeSpan(other, shapeByElement);

      return otherSource.y + otherSource.height / 2 ===
          shapeByElement.get(edge.sourceRef).y + shapeByElement.get(edge.sourceRef).height / 2 &&
        otherLeft > left && otherLeft < right;
    });
    const value = 1 + Math.max(0, ...nested.map(depth));

    memo.set(edge, value);
    return value;
  };

  return depth(flow);
}

function hasOverlappingUShape(flow, shapes, policy) {
  const shapeByElement = new Map(shapes.map(({ element, rect }) => [ element, rect ]));
  const source = shapeByElement.get(flow.sourceRef);
  const [ left, right ] = uShapeSpan(flow, shapeByElement);

  return uShapeCandidates(shapes, policy, shapeByElement).some(other => {
    if (other === flow) {
      return false;
    }

    const otherSource = shapeByElement.get(other.sourceRef);
    const [ otherLeft, otherRight ] = uShapeSpan(other, shapeByElement);

    return otherSource.y + otherSource.height / 2 ===
        source.y + source.height / 2 &&
      otherLeft < right &&
      otherRight > left;
  });
}

function uShapeCandidates(shapes, policy, shapeByElement) {
  return (policy.graphEdges || []).filter(edge => {
    const source = shapeByElement.get(edge.sourceRef);
    const target = shapeByElement.get(edge.targetRef);

    if (!source || !target ||
        source.y + source.height / 2 !== target.y + target.height / 2) {
      return false;
    }

    if (policy.backEdges?.has(edge)) {
      return true;
    }

    const start = point(source.x + source.width, source.y + source.height / 2);
    const end = point(target.x, target.y + target.height / 2);

    return target.x >= source.x &&
      !segmentIsClear(start, end, shapes, edge.sourceRef, edge.targetRef, []);
  });
}

function uShapeSpan(edge, shapeByElement) {
  const source = shapeByElement.get(edge.sourceRef);
  const target = shapeByElement.get(edge.targetRef);

  return [
    Math.min(source.x, target.x),
    Math.max(source.x + source.width, target.x + target.width)
  ];
}

function findOuterRoute(start, end, shapes, sourceElement, targetElement, routedConnections, isBack, sourceBoundary, sourceTop) {
  const extents = getShapeExtents(shapes);

  for (let attempt = 1; attempt <= MAX_ROUTE_SEARCH_ATTEMPTS; attempt++) {
    const spacing = ROUTING_MARGIN * attempt;
    const channelY = sourceBoundary && sourceTop
      ? extents.minY - spacing
      : extents.maxY + spacing;
    const exitX = isBack ? start.x - spacing : start.x + spacing;
    const entryX = isBack ? end.x + spacing : end.x - spacing;
    const candidate = cleanPoints([
      start,
      point(sourceBoundary ? start.x : exitX, start.y),
      point(sourceBoundary ? start.x : exitX, channelY),
      point(entryX, channelY),
      point(entryX, end.y),
      end
    ]);
    if (pathIsClear(candidate, shapes, sourceElement, targetElement, routedConnections)) {
      return candidate;
    }
  }

  return null;
}

function findPerimeterRoute(source, target, shapes, sourceElement, targetElement, routedConnections) {
  const extents = getShapeExtents(shapes);
  const corners = [
    point(extents.minX - ROUTING_MARGIN, extents.minY - ROUTING_MARGIN),
    point(extents.maxX + ROUTING_MARGIN, extents.minY - ROUTING_MARGIN),
    point(extents.minX - ROUTING_MARGIN, extents.maxY + ROUTING_MARGIN),
    point(extents.maxX + ROUTING_MARGIN, extents.maxY + ROUTING_MARGIN)
  ];

  for (const corner of corners) {
    const sourceLegs = outerLegs(source, corner);
    const targetLegs = outerLegs(target, corner);

    for (const sourceLeg of sourceLegs) {
      for (const targetLeg of targetLegs) {
        const route = cleanPoints([
          ...sourceLeg,
          ...targetLeg.slice().reverse().slice(1)
        ]);

        if (pathIsClear(route, shapes, sourceElement, targetElement, routedConnections)) {
          return route;
        }
      }
    }
  }

  return null;
}

function outerLegs(rect, corner) {
  const horizontalPort = point(
    corner.x < rect.x ? rect.x : rect.x + rect.width,
    rect.y + rect.height / 2
  );
  const verticalPort = point(
    rect.x + rect.width / 2,
    corner.y < rect.y ? rect.y : rect.y + rect.height
  );

  return [
    [ horizontalPort, point(corner.x, horizontalPort.y), corner ],
    [ verticalPort, point(verticalPort.x, corner.y), corner ]
  ];
}

function visibilityRoute(
    start,
    end,
    shapes,
    sourceElement,
    targetElement,
    routedConnections,
    obstacleInset = ROUTE_OBSTACLE_INSET,
    allowPerpendicularCrossings = false) {
  const extents = getShapeExtents(shapes);
  const xs = new Set([ start.x, end.x, extents.minX - ROUTING_MARGIN, extents.maxX + ROUTING_MARGIN ]);
  const ys = new Set([ start.y, end.y, extents.minY - ROUTING_MARGIN, extents.maxY + ROUTING_MARGIN ]);

  for (const { rect } of shapes) {
    xs.add(rect.x - ROUTING_MARGIN);
    xs.add(rect.x + rect.width + ROUTING_MARGIN);
    ys.add(rect.y - ROUTING_MARGIN);
    ys.add(rect.y + rect.height + ROUTING_MARGIN);
  }

  const points = [];

  for (const x of xs) {
    for (const y of ys) {
      const candidate = point(x, y);

      if (!isInsideAny(candidate, shapes, sourceElement, targetElement)) {
        points.push(candidate);
      }
    }
  }

  const startIndex = points.push(start) - 1;
  const endIndex = points.push(end) - 1;
  const { pointsByX, pointsByY } = indexVisibilityPoints(points);
  const distance = Array(points.length).fill(Infinity);
  const previous = Array(points.length).fill(-1);
  const pending = new Set(points.map((_, index) => index));
  const queue = [];
  const segmentIsClear = createVisibilitySegmentChecker(
    shapes,
    sourceElement,
    targetElement,
    routedConnections,
    obstacleInset,
    allowPerpendicularCrossings
  );
  distance[startIndex] = 0;
  pushVisibilityNode(queue, { index: startIndex, distance: 0 });

  while (queue.length) {
    const entry = popVisibilityNode(queue);
    const current = entry.index;

    if (!pending.has(current) || entry.distance !== distance[current]) {
      continue;
    }

    pending.delete(current);

    if (current === endIndex) {
      break;
    }

    const vertical = pointsByX.get(points[current].x) || [];
    const horizontal = pointsByY.get(points[current].y) || [];
    let verticalIndex = 0;
    let horizontalIndex = 0;

    while (verticalIndex < vertical.length || horizontalIndex < horizontal.length) {
      const verticalNext = vertical[verticalIndex] ?? Infinity;
      const horizontalNext = horizontal[horizontalIndex] ?? Infinity;
      const next = Math.min(verticalNext, horizontalNext);

      if (verticalNext === next) {
        verticalIndex++;
      }

      if (horizontalNext === next) {
        horizontalIndex++;
      }

      if (!pending.has(next)) {
        continue;
      }

      if (!segmentIsClear(points[current], points[next])) {
        continue;
      }

      const candidate = distance[current] + manhattan(points[current], points[next]) + VISIBILITY_GRAPH_TURN_PENALTY;

      if (candidate < distance[next]) {
        distance[next] = candidate;
        previous[next] = current;
        pushVisibilityNode(queue, { index: next, distance: candidate });
      }
    }
  }

  if (distance[endIndex] === Infinity) {
    return null;
  }

  const route = [];

  for (let current = endIndex; current !== -1; current = previous[current]) {
    route.unshift(points[current]);
  }

  return cleanPoints(route);
}

function createVisibilitySegmentChecker(
    shapes,
    sourceElement,
    targetElement,
    routedConnections,
    obstacleInset,
    allowPerpendicularCrossings) {
  const obstacles = shapes
    .filter(({ element }) => element !== sourceElement && element !== targetElement)
    .map(({ rect }) => inset(rect, obstacleInset));
  const connections = routedConnections.map(connection => {
    return {
      segments: toSegments(connection.points),
      sharedEndpoint: sharesEndpointChannel(
        connection.flow,
        sourceElement,
        targetElement
      )
    };
  });

  return (a, b) => {
    if (a.x === b.x && a.y === b.y) {
      return false;
    }

    if (obstacles.some(rect => segmentEntersRect(a, b, rect))) {
      return false;
    }

    return !connections.some(connection => {
      return connection.segments.some(([ c, d ]) => {
        return (!allowPerpendicularCrossings && segmentsProperlyCross(a, b, c, d)) ||
          (!connection.sharedEndpoint && collinearOverlap(a, b, c, d));
      });
    });
  };
}

function indexVisibilityPoints(points) {
  const pointsByX = new Map();
  const pointsByY = new Map();

  points.forEach((candidate, index) => {
    if (!Number.isNaN(candidate.x)) {
      appendVisibilityPoint(pointsByX, candidate.x, index);
    }

    if (!Number.isNaN(candidate.y)) {
      appendVisibilityPoint(pointsByY, candidate.y, index);
    }
  });

  return { pointsByX, pointsByY };
}

function appendVisibilityPoint(index, coordinate, pointIndex) {
  const points = index.get(coordinate);

  if (points) {
    points.push(pointIndex);
  } else {
    index.set(coordinate, [ pointIndex ]);
  }
}

function pushVisibilityNode(queue, entry) {
  queue.push(entry);

  let index = queue.length - 1;

  while (index > 0) {
    const parentIndex = Math.floor((index - 1) / 2);

    if (compareVisibilityNodes(queue[parentIndex], entry) <= 0) {
      break;
    }

    queue[index] = queue[parentIndex];
    index = parentIndex;
  }

  queue[index] = entry;
}

function popVisibilityNode(queue) {
  const first = queue[0];
  const last = queue.pop();

  if (queue.length) {
    let index = 0;

    while (index * 2 + 1 < queue.length) {
      const leftIndex = index * 2 + 1;
      const rightIndex = leftIndex + 1;

      const childIndex = rightIndex < queue.length &&
        compareVisibilityNodes(queue[rightIndex], queue[leftIndex]) < 0
        ? rightIndex
        : leftIndex;

      if (compareVisibilityNodes(last, queue[childIndex]) <= 0) {
        break;
      }

      queue[index] = queue[childIndex];
      index = childIndex;
    }

    queue[index] = last;
  }

  return first;
}

function compareVisibilityNodes(a, b) {
  return a.distance - b.distance || a.index - b.index;
}

function segmentIsClear(
    a,
    b,
    shapes,
    sourceElement,
    targetElement,
    routedConnections,
    obstacleInset = ROUTE_OBSTACLE_INSET,
    allowPerpendicularCrossings = false) {
  if (a.x === b.x && a.y === b.y) {
    return false;
  }

  for (const { element, rect } of shapes) {
    if (element === sourceElement || element === targetElement) {
      continue;
    }

    if (segmentEntersRect(a, b, inset(rect, obstacleInset))) {
      return false;
    }
  }

  return !routedConnections.some(connection => {
    const sharedEndpoint = sharesEndpointChannel(
      connection.flow,
      sourceElement,
      targetElement
    );

    return toSegments(connection.points).some(([ c, d ]) => {
      return (!allowPerpendicularCrossings && segmentsProperlyCross(a, b, c, d)) ||
        (!sharedEndpoint && collinearOverlap(a, b, c, d));
    });
  });
}

function sharesEndpointChannel(flow, source, target) {
  return flow.sourceRef === source ||
    flow.targetRef === target ||
    (flow.$instanceOf('bpmn:MessageFlow') && (
      (flow.sourceRef === target && flow.targetRef !== source) ||
      (flow.targetRef === source && flow.sourceRef !== target)
    ));
}

function pathIsClear(
    points,
    shapes,
    sourceElement,
    targetElement,
    routedConnections,
    obstacleInset = ROUTE_OBSTACLE_INSET) {
  return toSegments(points).every(([ a, b ]) => {
    return segmentIsClear(
      a,
      b,
      shapes,
      sourceElement,
      targetElement,
      routedConnections,
      obstacleInset
    );
  });
}

function isInsideAny(candidate, shapes, sourceElement, targetElement) {
  return shapes.some(({ element, rect }) => {
    return element !== sourceElement && element !== targetElement && pointInRect(candidate, rect);
  });
}

function findContainingArtifactContainers(elementBounds, shapes) {
  const center = {
    x: elementBounds.x + elementBounds.width / 2,
    y: elementBounds.y + elementBounds.height / 2
  };

  return [ ...shapes.entries() ]
    .filter(([ element, rect ]) => {
      return (
        is(element, 'bpmn:Lane') ||
        is(element, 'bpmn:Participant') ||
        is(element, 'bpmn:SubProcess')
      ) &&
      center.x >= rect.x &&
      center.x <= rect.x + rect.width &&
      center.y >= rect.y &&
      center.y <= rect.y + rect.height;
    })
    .map(([ , rect ]) => rect)
    .sort((a, b) => a.width * a.height - b.width * b.height);
}

function associationConnection(
    association,
    owner,
    ownerBounds,
    artifact,
    artifactBounds,
    connectionIndex = 0,
    connectionCount = 1,
    obstacles = [],
    routeSegments = []) {
  const artifactIsSource = is(association, 'bpmn:DataInputAssociation') ||
    association.sourceRef === artifact ||
    (Array.isArray(association.sourceRef) && association.sourceRef.includes(artifact));
  const dataArtifact = is(artifact, 'bpmn:DataObjectReference') ||
    is(artifact, 'bpmn:DataStoreReference');

  if (dataArtifact) {
    const points = orthogonalAssociationRoute(
      owner,
      ownerBounds,
      artifact,
      artifactBounds,
      obstacles,
      routeSegments,
      connectionIndex,
      connectionCount
    );

    return artifactIsSource ? points : points.reverse();
  }

  let ownerPoint;
  let artifactPoint;

  if (artifactBounds.y + artifactBounds.height <= ownerBounds.y) {
    const offset = associationDockOffset(
      ownerBounds.width,
      artifactBounds.width,
      connectionIndex,
      connectionCount
    );
    ownerPoint = point(ownerBounds.x + ownerBounds.width / 2 + offset, ownerBounds.y);
    artifactPoint = point(
      artifactBounds.x + artifactBounds.width / 2 + offset,
      artifactBounds.y + artifactBounds.height
    );
  } else if (ownerBounds.y + ownerBounds.height <= artifactBounds.y) {
    const offset = associationDockOffset(
      ownerBounds.width,
      artifactBounds.width,
      connectionIndex,
      connectionCount
    );
    ownerPoint = point(
      ownerBounds.x + ownerBounds.width / 2 + offset,
      ownerBounds.y + ownerBounds.height
    );
    artifactPoint = point(
      artifactBounds.x + artifactBounds.width / 2 + offset,
      artifactBounds.y
    );
  } else if (artifactBounds.x + artifactBounds.width <= ownerBounds.x) {
    const offset = associationDockOffset(
      ownerBounds.height,
      artifactBounds.height,
      connectionIndex,
      connectionCount
    );
    ownerPoint = point(ownerBounds.x, ownerBounds.y + ownerBounds.height / 2 + offset);
    artifactPoint = point(
      artifactBounds.x + artifactBounds.width,
      artifactBounds.y + artifactBounds.height / 2 + offset
    );
  } else if (ownerBounds.x + ownerBounds.width <= artifactBounds.x) {
    const offset = associationDockOffset(
      ownerBounds.height,
      artifactBounds.height,
      connectionIndex,
      connectionCount
    );
    ownerPoint = point(
      ownerBounds.x + ownerBounds.width,
      ownerBounds.y + ownerBounds.height / 2 + offset
    );
    artifactPoint = point(
      artifactBounds.x,
      artifactBounds.y + artifactBounds.height / 2 + offset
    );
  } else {
    const artifactAbove = artifactBounds.y + artifactBounds.height / 2 <
      ownerBounds.y + ownerBounds.height / 2;
    const offset = associationDockOffset(
      ownerBounds.width,
      artifactBounds.width,
      connectionIndex,
      connectionCount
    );
    ownerPoint = point(
      ownerBounds.x + ownerBounds.width / 2 + offset,
      artifactAbove ? ownerBounds.y : ownerBounds.y + ownerBounds.height
    );
    artifactPoint = point(
      artifactBounds.x + artifactBounds.width / 2 + offset,
      artifactAbove ? artifactBounds.y + artifactBounds.height : artifactBounds.y
    );
  }

  return artifactIsSource
    ? [ artifactPoint, ownerPoint ]
    : [ ownerPoint, artifactPoint ];
}

function associationDockOffset(ownerSpan, artifactSpan, index, count) {
  if (count < 2) {
    return 0;
  }

  const span = Math.min(ownerSpan, artifactSpan);

  return (index + 1) * span / (count + 1) - span / 2;
}

function orthogonalAssociationRoute(
    owner,
    ownerBounds,
    artifact,
    artifactBounds,
    obstacles,
    routeSegments,
    connectionIndex,
    connectionCount) {
  const horizontal = horizontalAssociationDocks(
    artifactBounds,
    ownerBounds,
    connectionIndex,
    connectionCount
  );
  const vertical = verticalAssociationDocks(
    artifactBounds,
    ownerBounds,
    connectionIndex,
    connectionCount
  );
  const centeredIndex = connectionIndex - (connectionCount - 1) / 2;
  const channelOffset = centeredIndex * ROUTING_MARGIN;
  const candidates = [
    {
      points: [
        horizontal.artifact,
        point(vertical.owner.x, horizontal.artifact.y),
        vertical.owner
      ],
      artifactAxis: 'horizontal',
      ownerAxis: 'vertical'
    },
    {
      points: [
        vertical.artifact,
        point(vertical.artifact.x, horizontal.owner.y),
        horizontal.owner
      ],
      artifactAxis: 'vertical',
      ownerAxis: 'horizontal'
    },
    {
      points: [
        horizontal.artifact,
        point(
          (horizontal.artifact.x + horizontal.owner.x) / 2 + channelOffset,
          horizontal.artifact.y
        ),
        point(
          (horizontal.artifact.x + horizontal.owner.x) / 2 + channelOffset,
          horizontal.owner.y
        ),
        horizontal.owner
      ],
      artifactAxis: 'horizontal',
      ownerAxis: 'horizontal'
    },
    {
      points: [
        vertical.artifact,
        point(
          vertical.artifact.x,
          (vertical.artifact.y + vertical.owner.y) / 2 + channelOffset
        ),
        point(
          vertical.owner.x,
          (vertical.artifact.y + vertical.owner.y) / 2 + channelOffset
        ),
        vertical.owner
      ],
      artifactAxis: 'vertical',
      ownerAxis: 'vertical'
    }
  ].map(candidate => ({
    ...candidate,
    points: simplifyOrthogonalRoute(candidate.points)
  })).filter(({ points, artifactAxis, ownerAxis }) => {
    return routeHasDockDirections(points, artifactAxis, ownerAxis);
  });

  return candidates.map(({ points }, index) => {
    const clear = routeAvoidsEndpointInteriors(
      points,
      artifactBounds,
      ownerBounds
    ) && pathIsClear(
      points,
      obstacles,
      owner,
      artifact,
      [],
      -1
    );

    return {
      points,
      score: [
        clear ? 0 : 1,
        routeCrossings(points, routeSegments),
        points.length - 2,
        index,
        routeLength(points)
      ]
    };
  }).sort((a, b) => compareScores(a.score, b.score))[0].points;
}

function routeAvoidsEndpointInteriors(points, artifactBounds, ownerBounds) {
  return toSegments(points).every(([ start, end ]) => {
    return !segmentEntersRect(start, end, artifactBounds) &&
      !segmentEntersRect(start, end, ownerBounds);
  });
}

function routeHasDockDirections(points, artifactAxis, ownerAxis) {
  const first = points[0];
  const second = points[1];
  const penultimate = points.at(-2);
  const last = points.at(-1);
  const firstAxis = first.y === second.y ? 'horizontal' : 'vertical';
  const lastAxis = penultimate.y === last.y ? 'horizontal' : 'vertical';

  return firstAxis === artifactAxis && lastAxis === ownerAxis;
}

function horizontalAssociationDocks(
    artifactBounds,
    ownerBounds,
    connectionIndex,
    connectionCount) {
  const artifactLeft = artifactBounds.x + artifactBounds.width / 2 <
    ownerBounds.x + ownerBounds.width / 2;
  const offset = associationDockOffset(
    ownerBounds.height,
    artifactBounds.height,
    connectionIndex,
    connectionCount
  );

  return {
    artifact: point(
      artifactLeft
        ? artifactBounds.x + artifactBounds.width
        : artifactBounds.x,
      artifactBounds.y + artifactBounds.height / 2 + offset
    ),
    owner: point(
      artifactLeft
        ? ownerBounds.x
        : ownerBounds.x + ownerBounds.width,
      ownerBounds.y + ownerBounds.height / 2 + offset
    )
  };
}

function verticalAssociationDocks(
    artifactBounds,
    ownerBounds,
    connectionIndex,
    connectionCount) {
  const artifactAbove = artifactBounds.y + artifactBounds.height / 2 <
    ownerBounds.y + ownerBounds.height / 2;
  const offset = associationDockOffset(
    ownerBounds.width,
    artifactBounds.width,
    connectionIndex,
    connectionCount
  );

  return {
    artifact: point(
      artifactBounds.x + artifactBounds.width / 2 + offset,
      artifactAbove
        ? artifactBounds.y + artifactBounds.height
        : artifactBounds.y
    ),
    owner: point(
      ownerBounds.x + ownerBounds.width / 2 + offset,
      artifactAbove
        ? ownerBounds.y
        : ownerBounds.y + ownerBounds.height
    )
  };
}

function simplifyOrthogonalRoute(points) {
  const deduplicated = points.filter((candidate, index) => {
    const previous = points[index - 1];

    return !previous ||
      candidate.x !== previous.x ||
      candidate.y !== previous.y;
  });

  return deduplicated.filter((candidate, index) => {
    const previous = deduplicated[index - 1];
    const next = deduplicated[index + 1];

    return !previous || !next || !(
      (previous.x === candidate.x && candidate.x === next.x) ||
      (previous.y === candidate.y && candidate.y === next.y)
    );
  });
}

function artifactSizeCandidates(element) {
  if (!is(element, 'bpmn:TextAnnotation')) {
    return [ getDefaultSize(element) ];
  }

  const text = element.text || '';
  const candidates = [];

  for (
    let width = ANNOTATION_MIN_WIDTH;
    width <= ANNOTATION_MAX_WIDTH;
    width += ANNOTATION_WIDTH_STEP
  ) {
    const lineCount = wrappedTextLineCount(text, width);
    const height = Math.max(
      ANNOTATION_MIN_HEIGHT,
      lineCount * ANNOTATION_LINE_HEIGHT + 2 * ANNOTATION_PADDING
    );

    candidates.push({ width, height });
  }

  return candidates.sort((a, b) => {
    return annotationSizePenalty(a) - annotationSizePenalty(b) ||
      a.width * a.height - b.width * b.height ||
      a.width - b.width;
  });
}

function wrappedTextLineCount(text, width) {
  const capacity = Math.max(
    1,
    Math.floor((width - 2 * ANNOTATION_PADDING) / ANNOTATION_CHARACTER_WIDTH)
  );

  return String(text || '').split(/\r?\n/).reduce((total, paragraph) => {
    const words = paragraph.trim().split(/\s+/).filter(Boolean);

    if (!words.length) {
      return total + 1;
    }

    let lines = 1;
    let lineLength = 0;

    for (const word of words) {
      const wordLength = word.length;

      if (!lineLength) {
        lines += Math.max(0, Math.ceil(wordLength / capacity) - 1);
        lineLength = wordLength % capacity || Math.min(wordLength, capacity);
      } else if (lineLength + 1 + wordLength <= capacity) {
        lineLength += 1 + wordLength;
      } else {
        lines += Math.max(1, Math.ceil(wordLength / capacity));
        lineLength = wordLength % capacity || Math.min(wordLength, capacity);
      }
    }

    return total + lines;
  }, 0);
}

function annotationSizePenalty(size) {
  return Math.round(
    Math.abs(size.width / size.height - ANNOTATION_TARGET_ASPECT_RATIO) *
    ANNOTATION_ASPECT_RATIO_PENALTY_SCALE
  );
}

function findArtifactPlacement(
    artifact,
    ownerBounds,
    references,
    sizes,
    obstacles,
    routes,
    occupied,
    container,
    processContainer,
    extents,
    annotationClearance = 0,
    boundaryContainers = [],
    avoidParticipantInterior = false,
    preferParticipantSides = true,
    participantInteriorPreference = 0) {
  const preparedBoundaryContainers = boundaryContainers.map(({
    rect,
    containsOwner,
    participant
  }) => {
    return {
      containsOwner,
      participant,
      rect,
      headerRect: participant && {
        x: rect.x,
        y: rect.y,
        width: PARTICIPANT_HEADER_WIDTH,
        height: rect.height
      }
    };
  });
  const referenceOwnerBounds = [ ...new Set(
    references.map(reference => reference.ownerBounds).filter(Boolean)
  ) ];
  const hasMultipleOwners = referenceOwnerBounds.length > 1;
  const localCandidates = ownerBounds
    ? referenceOwnerBounds.flatMap(referenceBounds => {
      return ownedArtifactCandidates(artifact, referenceBounds, sizes);
    })
    : unownedArtifactCandidates(sizes, extents);
  const candidates = is(artifact, 'bpmn:TextAnnotation')
    ? [
      ...localCandidates,
      ...participantExteriorArtifactCandidates(
        sizes,
        ownerBounds,
        preparedBoundaryContainers,
        annotationClearance
      ),
      ...outerScopeArtifactCandidates(sizes, extents)
    ]
    : localCandidates;
  const obstacleBounds = obstacles.map(({ element, rect }) => {
    return is(element, 'bpmn:BoundaryEvent')
      ? artifactObstacleBounds(rect)
      : rect;
  });
  const occupiedBounds = occupied.map(({ element, rect, annotationClearance }) => {
    return artifactClearanceBounds(element, rect, annotationClearance);
  });
  const routeSegments = routes.flatMap(route => toSegments(route.points));
  const dataArtifact = is(artifact, 'bpmn:DataObjectReference') ||
    is(artifact, 'bpmn:DataStoreReference');
  const requiresContainment = is(artifact, 'bpmn:DataObjectReference');
  const rankedCandidates = candidates.map(candidate => {
    const routedReferences = references.map(({
      association,
      owner,
      ownerBounds,
      ownerConnectionIndex,
      ownerConnectionCount
    }) => {
      const points = ownerBounds && associationConnection(
        association,
        owner,
        ownerBounds,
        artifact,
        candidate.rect,
        ownerConnectionIndex,
        ownerConnectionCount,
        obstacles,
        routeSegments
      );
      return points && { owner, points };
    }).filter(Boolean);
    const directLength = dataArtifact
      ? ownerBalancedRouteLength(routedReferences)
      : routedReferences.reduce((total, { points }) => {
        return total + routeLength(points);
      }, 0);
    const associationCrossingCount = routedReferences.reduce((total, { points }) => {
      return total + routeCrossings(points, routeSegments);
    }, 0);
    const associationBendCount = dataArtifact
      ? routedReferences.reduce((total, { points }) => {
        return total + Math.max(0, points.length - 2);
      }, 0)
      : 0;
    const alignedOwnerCount = dataArtifact
      ? referenceOwnerBounds.filter(referenceBounds => {
        return artifactIsAligned(candidate.rect, referenceBounds);
      }).length
      : 0;
    const missesOwnerAlignment = dataArtifact &&
      referenceOwnerBounds.length > 0 &&
      alignedOwnerCount === 0 ? 1 : 0;
    const congestionPenalty = hasMultipleOwners
      ? artifactCongestionPenalty(candidate.rect, obstacleBounds)
      : 0;
    const congestionViolation = congestionPenalty > 0 ? 1 : 0;
    const participantInteriorPenalty = (
      avoidParticipantInterior ||
      participantInteriorPreference
    ) &&
      is(artifact, 'bpmn:TextAnnotation') &&
      preparedBoundaryContainers.some(({
        rect,
        containsOwner,
        participant
      }) => {
        return participant &&
          containsOwner &&
          artifactFitsContainer(candidate.rect, rect, 0);
      }) ? 1 : 0;
    const participantVerticalSidePenalty = preferParticipantSides &&
      is(artifact, 'bpmn:TextAnnotation') &&
      preparedBoundaryContainers.some(({
        rect,
        containsOwner,
        participant
      }) => {
        return participant &&
          containsOwner &&
          (
            candidate.rect.y + candidate.rect.height <= rect.y ||
            candidate.rect.y >= rect.y + rect.height
          );
      }) ? 1 : 0;
    const lowerBound = [
      avoidParticipantInterior ? participantInteriorPenalty : 0,
      dataArtifact ? associationCrossingCount : 0,
      congestionViolation,
      missesOwnerAlignment,
      -alignedOwnerCount,
      associationBendCount,
      directLength +
        congestionPenalty +
        participantInteriorPenalty * participantInteriorPreference +
        participantVerticalSidePenalty * 4 * VERTICAL_GAP,
      directLength,
      is(artifact, 'bpmn:TextAnnotation')
        ? annotationSizePenalty(candidate.rect)
        : 0,
      dataArtifact ? 0 : associationCrossingCount,
      0,
      artifactExpansion(candidate.rect, extents),
      candidate.sideRank,
      Math.abs(candidate.offset),
      candidate.gap,
      candidate.rect.y,
      candidate.rect.x
    ];

    return {
      candidate,
      lowerBound,
      alignedOwnerCount,
      associationBendCount,
      associationCrossingCount,
      congestionPenalty,
      congestionViolation,
      missesOwnerAlignment,
      participantInteriorPenalty,
      participantVerticalSidePenalty,
      routedReferences
    };
  }).sort((a, b) => compareScores(a.lowerBound, b.lowerBound));

  let best;

  for (const {
    candidate,
    lowerBound,
    alignedOwnerCount,
    associationBendCount,
    associationCrossingCount,
    congestionPenalty,
    congestionViolation,
    missesOwnerAlignment,
    participantInteriorPenalty,
    participantVerticalSidePenalty,
    routedReferences
  } of rankedCandidates) {
    if (best && compareScores(lowerBound, best.score) >= 0) {
      break;
    }

    const candidateBounds = artifactClearanceBounds(
      artifact,
      candidate.rect,
      annotationClearance
    );
    const fitsContainer = artifactFitsContainer(candidateBounds, container, 0);
    const fitsProcessContainer = artifactFitsContainer(
      candidateBounds,
      processContainer,
      0
    );
    const straddlesContainer = container &&
      rectanglesOverlap(candidateBounds, container) &&
      !fitsContainer;
    const straddlesProcessContainer = processContainer &&
      rectanglesOverlap(candidateBounds, processContainer) &&
      !fitsProcessContainer;
    const violatesContainerBoundary = preparedBoundaryContainers.some(({
      rect,
      containsOwner,
      participant,
      headerRect
    }) => {
      if (participant && rectanglesOverlap(candidateBounds, headerRect)) {
        return true;
      }

      if (!rectanglesOverlap(candidateBounds, rect)) {
        return false;
      }

      return !containsOwner || !artifactFitsContainer(candidateBounds, rect, 0);
    });
    const overlapsParticipantHeader = container &&
      candidateBounds.y < container.y + container.height &&
      candidateBounds.y + candidateBounds.height > container.y &&
      candidateBounds.x < container.x &&
      candidateBounds.x + candidateBounds.width >
        container.x - PARTICIPANT_HEADER_WIDTH;

    if ((requiresContainment && !fitsContainer) ||
        straddlesContainer ||
        straddlesProcessContainer ||
        violatesContainerBoundary ||
        overlapsParticipantHeader ||
        obstacleBounds.some(obstacle => {
          return rectanglesOverlap(candidateBounds, obstacle);
        }) ||
        occupiedBounds.some(occupied => {
          return rectanglesOverlap(candidateBounds, occupied);
        }) ||
        artifactIntersectsRoutes(candidate.rect, routeSegments)) {
      continue;
    }

    const directPathsClear = routedReferences.every(({ owner, points }) => {
      return pathIsClear(
        points,
        obstacles,
        owner,
        artifact,
        []
      );
    });

    if (!directPathsClear) {
      continue;
    }

    const associationRoutes = routedReferences.map(({ points }) => points);
    const associationLength = dataArtifact
      ? ownerBalancedRouteLength(routedReferences)
      : associationRoutes.reduce((total, points) => {
        return total + routeLength(points);
      }, 0);
    const nonStraightPenalty = is(artifact, 'bpmn:TextAnnotation')
      ? associationRoutes.reduce((total, points) => {
        return total + (
          isStraightOrthogonalRoute(points)
            ? 0
            : NON_STRAIGHT_ARTIFACT_ASSOCIATION_PENALTY
        );
      }, 0)
      : 0;
    const score = [
      avoidParticipantInterior ? participantInteriorPenalty : 0,
      dataArtifact ? associationCrossingCount : 0,
      congestionViolation,
      missesOwnerAlignment,
      -alignedOwnerCount,
      associationBendCount,
      associationLength +
        congestionPenalty +
        nonStraightPenalty +
        participantInteriorPenalty * participantInteriorPreference +
        participantVerticalSidePenalty * 4 * VERTICAL_GAP,
      associationLength,
      is(artifact, 'bpmn:TextAnnotation')
        ? annotationSizePenalty(candidate.rect)
        : 0,
      dataArtifact ? 0 : associationCrossingCount,
      !is(artifact, 'bpmn:TextAnnotation') && (
        (container && !fitsContainer) ||
        (processContainer && !fitsProcessContainer)
      ) ? 1 : 0,
      artifactExpansion(candidate.rect, extents),
      candidate.sideRank,
      Math.abs(candidate.offset),
      candidate.gap,
      candidate.rect.y,
      candidate.rect.x
    ];

    if (!best || compareScores(score, best.score) < 0) {
      best = { candidate: candidate.rect, score };
    }
  }

  if (best) {
    return best.candidate;
  }

  throw new LayoutError(
    'ARTIFACT_PLACEMENT_FAILED',
    artifact.id,
    `No collision-free artifact position could be found (${artifact.id}).`
  );
}

function artifactIsAligned(artifact, owner) {
  return artifact.x + artifact.width / 2 === owner.x + owner.width / 2 ||
    artifact.y + artifact.height / 2 === owner.y + owner.height / 2;
}

function ownerBalancedRouteLength(routedReferences) {
  const ownerRoutes = new Map();

  for (const { owner, points } of routedReferences) {
    const lengths = ownerRoutes.get(owner) || [];

    lengths.push(routeLength(points));
    ownerRoutes.set(owner, lengths);
  }

  return [ ...ownerRoutes.values() ].reduce((total, lengths) => {
    return total + lengths.reduce((sum, length) => sum + length, 0) / lengths.length;
  }, 0);
}

function artifactCongestionPenalty(rect, obstacles) {
  const nearestDistance = obstacles.reduce((nearest, obstacle) => {
    return Math.min(nearest, rectangleDistance(rect, obstacle));
  }, Infinity);

  return Math.max(0, 2 * ROUTING_MARGIN - nearestDistance);
}

function rectangleDistance(a, b) {
  const horizontal = Math.max(a.x - b.x - b.width, b.x - a.x - a.width, 0);
  const vertical = Math.max(a.y - b.y - b.height, b.y - a.y - a.height, 0);

  return Math.hypot(horizontal, vertical);
}

function ownedArtifactCandidates(artifact, owner, sizes) {
  const candidates = [];
  const preferredSides = is(artifact, 'bpmn:DataObjectReference')
    ? [ 'below', 'right', 'left', 'above' ]
    : [ 'above', 'left', 'right', 'below' ];
  const offsets = [ 0 ];

  for (let distance = ROUTING_MARGIN; distance <= MAX_ARTIFACT_SEARCH_OFFSET; distance += ROUTING_MARGIN) {
    offsets.push(distance, -distance);
  }

  for (const size of sizes) {
    for (let sideRank = 0; sideRank < preferredSides.length; sideRank++) {
      const side = preferredSides[sideRank];

      for (let gap = ROUTING_MARGIN; gap <= MAX_ARTIFACT_GAP_STEPS * ROUTING_MARGIN; gap += ROUTING_MARGIN) {
        for (const offset of offsets) {
          candidates.push({
            rect: artifactBoundsAt(owner, size, side, gap, offset),
            sideRank,
            offset,
            gap
          });
        }
      }
    }
  }

  return candidates;
}

function artifactBoundsAt(owner, size, side, gap, offset) {
  if (side === 'above') {
    return bounds(
      owner.x + (owner.width - size.width) / 2 + offset,
      owner.y - gap - size.height,
      size.width,
      size.height
    );
  }
  if (side === 'below') {
    return bounds(
      owner.x + (owner.width - size.width) / 2 + offset,
      owner.y + owner.height + gap,
      size.width,
      size.height
    );
  }
  if (side === 'left') {
    return bounds(
      owner.x - gap - size.width,
      owner.y + (owner.height - size.height) / 2 + offset,
      size.width,
      size.height
    );
  }

  return bounds(
    owner.x + owner.width + gap,
    owner.y + (owner.height - size.height) / 2 + offset,
    size.width,
    size.height
  );
}

function artifactClearanceBounds(artifact, rect, clearance) {
  if (!clearance || !is(artifact, 'bpmn:TextAnnotation')) {
    return rect;
  }

  return bounds(
    rect.x - clearance,
    rect.y - clearance,
    rect.width + 2 * clearance,
    rect.height + 2 * clearance
  );
}

function unownedArtifactCandidates(sizes, extents) {
  const candidates = [];

  for (const size of sizes) {
    for (let offset = 0; offset <= extents.width + size.width; offset += ROUTING_MARGIN) {
      candidates.push({
        rect: bounds(
          extents.minX + offset,
          extents.minY - VERTICAL_GAP - size.height,
          size.width,
          size.height
        ),
        sideRank: 0,
        offset,
        gap: VERTICAL_GAP
      });
    }
  }

  return candidates;
}

function participantExteriorArtifactCandidates(
    sizes,
    ownerBounds,
    boundaryContainers,
    annotationClearance) {
  if (!ownerBounds) {
    return [];
  }

  const participants = boundaryContainers.filter(({
    containsOwner,
    participant
  }) => {
    return containsOwner && participant;
  });

  return participants.flatMap(({ rect }) => {
    return sizes.flatMap(size => {
      const x = ownerBounds.x + (ownerBounds.width - size.width) / 2;
      const gap = Math.max(ROUTING_MARGIN, annotationClearance);

      return [
        {
          rect: bounds(
            x,
            rect.y - gap - size.height,
            size.width,
            size.height
          ),
          sideRank: 4,
          offset: 0,
          gap
        },
        {
          rect: bounds(
            x,
            rect.y + rect.height + gap,
            size.width,
            size.height
          ),
          sideRank: 4,
          offset: 0,
          gap
        }
      ];
    });
  });
}

function outerScopeArtifactCandidates(sizes, extents) {
  const candidates = [];

  for (const size of sizes) {
    const horizontalPositions = axisPositions(
      extents.minX,
      extents.maxX - size.width
    );
    const verticalPositions = axisPositions(
      extents.minY,
      extents.maxY - size.height
    );

    for (const x of horizontalPositions) {
      candidates.push({
        rect: bounds(
          x,
          extents.minY - ROUTING_MARGIN - size.height,
          size.width,
          size.height
        ),
        sideRank: 4,
        offset: 0,
        gap: ROUTING_MARGIN
      });
      candidates.push({
        rect: bounds(
          x,
          extents.maxY + ROUTING_MARGIN,
          size.width,
          size.height
        ),
        sideRank: 4,
        offset: 0,
        gap: ROUTING_MARGIN
      });
    }

    for (const y of verticalPositions) {
      candidates.push({
        rect: bounds(
          extents.minX - ROUTING_MARGIN - size.width,
          y,
          size.width,
          size.height
        ),
        sideRank: 4,
        offset: 0,
        gap: ROUTING_MARGIN
      });
      candidates.push({
        rect: bounds(
          extents.maxX + ROUTING_MARGIN,
          y,
          size.width,
          size.height
        ),
        sideRank: 4,
        offset: 0,
        gap: ROUTING_MARGIN
      });
    }
  }

  return candidates;
}

function axisPositions(min, max) {
  if (max <= min) {
    return [ Math.round((min + max) / 2) ];
  }

  const positions = [];

  for (let position = min; position <= max; position += ROUTING_MARGIN) {
    positions.push(position);
  }

  if (positions.at(-1) !== max) {
    positions.push(max);
  }

  return positions;
}

function artifactFitsContainer(candidate, container, margin = ROUTING_MARGIN) {
  return !container ||
    candidate.x >= container.x + margin &&
    candidate.y >= container.y + margin &&
    candidate.x + candidate.width <=
      container.x + container.width - margin &&
    candidate.y + candidate.height <=
      container.y + container.height - margin;
}

function artifactObstacleBounds(rect) {
  return {
    x: rect.x - BOUNDARY_EVENT_ARTIFACT_CLEARANCE,
    y: rect.y - BOUNDARY_EVENT_ARTIFACT_CLEARANCE,
    width: rect.width + 2 * BOUNDARY_EVENT_ARTIFACT_CLEARANCE,
    height: rect.height + 2 * BOUNDARY_EVENT_ARTIFACT_CLEARANCE
  };
}

function artifactIntersectsRoutes(rect, routeSegments) {
  const clearance = {
    x: rect.x - ROUTING_MARGIN / 2,
    y: rect.y - ROUTING_MARGIN / 2,
    width: rect.width + ROUTING_MARGIN,
    height: rect.height + ROUTING_MARGIN
  };

  return routeSegments.some(([ start, end ]) => {
    return segmentEntersRect(start, end, clearance);
  });
}

function routeCrossings(points, routeSegments) {
  return toSegments(points).reduce((total, [ start, end ]) => {
    return total + routeSegments.filter(([ a, b ]) => {
      return segmentsProperlyCross(start, end, a, b);
    }).length;
  }, 0);
}

function artifactExpansion(rect, extents) {
  return Math.max(0, extents.minX - rect.x) +
    Math.max(0, extents.minY - rect.y) +
    Math.max(0, rect.x + rect.width - extents.maxX) +
    Math.max(0, rect.y + rect.height - extents.maxY);
}

function isStraightOrthogonalRoute(points) {
  return points.every(({ x }) => x === points[0].x) ||
    points.every(({ y }) => y === points[0].y);
}

function routeArtifactAssociation(
    association,
    owner,
    ownerBounds,
    artifact,
    artifactBounds,
    connectionIndex,
    connectionCount,
    obstacles = [],
    routes = []) {
  return associationConnection(
    association,
    owner,
    ownerBounds,
    artifact,
    artifactBounds,
    connectionIndex,
    connectionCount,
    obstacles,
    routes.flatMap(route => toSegments(route.points))
  );
}

function routeMessageFlow(
    source,
    target,
    sourceBounds,
    targetBounds,
    collaboration,
    participantShapes,
    obstacles,
    routedConnections,
    channelOffset) {
  const sourceCenterY = sourceBounds.y + sourceBounds.height / 2;
  const targetCenterY = targetBounds.y + targetBounds.height / 2;

  if (sourceCenterY === targetCenterY) {
    return directConnection(sourceBounds, targetBounds);
  }

  const downward = targetCenterY > sourceCenterY;
  const start = point(
    sourceBounds.x + sourceBounds.width / 2,
    downward ? sourceBounds.y + sourceBounds.height : sourceBounds.y
  );
  const end = point(
    targetBounds.x + targetBounds.width / 2,
    downward ? targetBounds.y : targetBounds.y + targetBounds.height
  );
  const sourceParticipant = findEndpointParticipant(source, collaboration);
  const targetParticipant = findEndpointParticipant(target, collaboration);
  const sourceParticipantBounds = participantShapes.get(sourceParticipant);
  const targetParticipantBounds = participantShapes.get(targetParticipant);
  const participantRows = [ ...new Set(
    [ ...(collaboration.participants || []) ]
      .map(participant => participantShapes.get(participant).y)
      .sort((a, b) => a - b)
  ) ];
  const sourceIndex = participantRows.indexOf(sourceParticipantBounds?.y);
  const targetIndex = participantRows.indexOf(targetParticipantBounds?.y);

  if (!sourceParticipantBounds || !targetParticipantBounds ||
      sourceIndex === -1 || targetIndex === -1) {
    return visibilityRoute(
      start,
      end,
      obstacles,
      source,
      target,
      routedConnections,
      MESSAGE_FLOW_OBSTACLE_INSET,
      true
    ) ||
      directConnection(sourceBounds, targetBounds);
  }

  const sourcePoolEdgeY = downward
    ? sourceParticipantBounds.y + sourceParticipantBounds.height
    : sourceParticipantBounds.y;
  const targetPoolEdgeY = downward
    ? targetParticipantBounds.y
    : targetParticipantBounds.y + targetParticipantBounds.height;
  const sourceObstacles = obstacles.filter(({ rect }) => centerIsInside(rect, sourceParticipantBounds));
  const targetObstacles = obstacles.filter(({ rect }) => centerIsInside(rect, targetParticipantBounds));
  const sourceIsParticipant = source === sourceParticipant;
  const targetIsParticipant = target === targetParticipant;
  const sourceDockX = findMessageFlowDockX(
    sourceBounds,
    downward,
    true,
    sourceIsParticipant ? 0 : channelOffset,
    obstacles,
    source,
    target,
    routedConnections
  );
  const targetDockX = findMessageFlowDockX(
    targetBounds,
    downward,
    false,
    targetIsParticipant ? 0 : channelOffset,
    obstacles,
    source,
    target,
    routedConnections
  );

  start.x = sourceDockX;
  end.x = targetDockX;

  if (sourceIsParticipant && !targetIsParticipant) {
    start.x = constrainParticipantDockX(targetDockX, sourceParticipantBounds);
  } else if (targetIsParticipant && !sourceIsParticipant) {
    end.x = constrainParticipantDockX(sourceDockX, targetParticipantBounds);
  } else if (sourceIsParticipant && targetIsParticipant) {
    const overlapCenter = (
      Math.max(sourceParticipantBounds.x, targetParticipantBounds.x) +
      Math.min(
        sourceParticipantBounds.x + sourceParticipantBounds.width,
        targetParticipantBounds.x + targetParticipantBounds.width
      )
    ) / 2 + channelOffset;

    start.x = overlapCenter;
    end.x = overlapCenter;
  }

  if ((sourceIsParticipant || targetIsParticipant) && start.x === end.x) {

    if (segmentIsClear(start, end, obstacles, source, target, [])) {
      return [ start, end ];
    }

    const verticalBypass = findMessageFlowVerticalBypass(
      source,
      target,
      sourceBounds,
      targetBounds,
      sourceIsParticipant,
      targetIsParticipant,
      start,
      end,
      obstacles,
      channelOffset
    );

    if (verticalBypass) {
      return verticalBypass;
    }

    const localBypass = visibilityRoute(start, end, obstacles, source, target, []);

    if (localBypass) {
      return localBypass;
    }
  } else if (start.x === end.x &&
      segmentIsClear(start, end, obstacles, source, target, [])) {
    return [ start, end ];
  }

  if (Math.abs(sourceIndex - targetIndex) === 1) {
    const channelY = Math.round((sourcePoolEdgeY + targetPoolEdgeY) / 2);
    const sourceChannel = point(start.x, channelY);
    const targetChannel = point(end.x, channelY);

    return cleanPoints([
      ...routeMessageLeg(start, sourceChannel, sourceObstacles, source, target, routedConnections),
      targetChannel,
      ...routeMessageLeg(targetChannel, end, targetObstacles, source, target, routedConnections).slice(1)
    ]);
  }

  const direction = downward ? 1 : -1;
  const sourceChannelY = sourcePoolEdgeY + direction * ROUTING_MARGIN;
  const targetChannelY = targetPoolEdgeY - direction * ROUTING_MARGIN;
  const sourceChannel = point(start.x, sourceChannelY);
  const targetChannel = point(end.x, targetChannelY);
  const participantBounds = [ ...participantShapes.values() ];
  const largeCollaboration = (collaboration.participants || []).length >
    MAX_EXHAUSTIVE_PARTICIPANT_COUNT;
  const firstRowY = Math.min(sourceParticipantBounds.y, targetParticipantBounds.y);
  const lastRowY = Math.max(sourceParticipantBounds.y, targetParticipantBounds.y);
  const interveningParticipants = participantBounds.filter(rect => {
    return rect.y > firstRowY && rect.y < lastRowY;
  });
  const rightExteriorX = Math.max(
    ...participantBounds.map(rect => rect.x + rect.width)
  ) + ROUTING_MARGIN;
  const channelXs = largeCollaboration
    ? [
      start.x,
      end.x,
      ...interveningParticipants.flatMap(rect => [
        rect.x - ROUTING_MARGIN,
        rect.x + rect.width + ROUTING_MARGIN
      ]),
      Math.min(...participantBounds.map(rect => rect.x)) - ROUTING_MARGIN,
      rightExteriorX
    ]
    : [ rightExteriorX ];
  const channelX = [ ...new Set(channelXs) ].filter(candidateX => {
    return interveningParticipants.every(rect => {
      return candidateX <= rect.x - ROUTING_MARGIN ||
        candidateX >= rect.x + rect.width + ROUTING_MARGIN;
    });
  }).sort((a, b) => {
    const aDistance = Math.abs(start.x - a) + Math.abs(end.x - a);
    const bDistance = Math.abs(start.x - b) + Math.abs(end.x - b);

    return aDistance - bDistance;
  })[0];

  return cleanPoints([
    ...routeMessageLeg(
      start,
      sourceChannel,
      sourceObstacles,
      source,
      target,
      routedConnections
    ),
    point(channelX, sourceChannelY),
    point(channelX, targetChannelY),
    targetChannel,
    ...routeMessageLeg(
      targetChannel,
      end,
      targetObstacles,
      source,
      target,
      routedConnections
    ).slice(1)
  ]);
}

function alignParticipantsHorizontally(
    collaboration,
    participantShapes,
    endpointShapes,
    connectionRoutes,
    channelOffsets,
    anchorPositionedParticipants,
    expandableParticipants) {
  const participants = collaboration.participants || [];
  const processParticipants = participants.filter(participant => participant.processRef);
  const anchorParticipant = processParticipants.reduce((largest, participant) => {
    if (!largest) {
      return participant;
    }

    const rect = participantShapes.get(participant);
    const largestRect = participantShapes.get(largest);

    return rect.width * rect.height > largestRect.width * largestRect.height
      ? participant
      : largest;
  }, null);
  const movableParticipants = processParticipants.filter(participant => {
    return participant !== anchorParticipant;
  });
  const initialPositions = new Map(participants.map(participant => {
    return [ participant, participantShapes.get(participant).x ];
  }));
  const positions = new Map(initialPositions);
  const scoreCache = new Map();
  const scorePositions = candidatePositions => {
    const key = participants
      .map(participant => candidatePositions.get(participant))
      .join(':');

    if (!scoreCache.has(key)) {
      scoreCache.set(key, horizontalAlignmentScore(
        candidatePositions,
        initialPositions,
        collaboration,
        participantShapes,
        endpointShapes,
        connectionRoutes,
        channelOffsets,
        anchorPositionedParticipants,
        expandableParticipants
      ));
    }

    return scoreCache.get(key);
  };
  const endpointRecords = (collaboration.messageFlows || []).map(messageFlow => {
    const source = resolveMessageFlowEndpoint(messageFlow.sourceRef, endpointShapes);
    const target = resolveMessageFlowEndpoint(messageFlow.targetRef, endpointShapes);
    const sourceParticipant = findEndpointParticipant(source, collaboration);
    const targetParticipant = findEndpointParticipant(target, collaboration);

    return {
      messageFlow,
      source,
      target,
      sourceParticipant,
      targetParticipant
    };
  }).filter(record => {
    return record.source &&
        record.target &&
        record.sourceParticipant &&
        record.targetParticipant &&
        record.sourceParticipant !== record.targetParticipant &&
        (
          movableParticipants.includes(record.sourceParticipant) ||
          movableParticipants.includes(record.targetParticipant)
        );
  });

  if (!endpointRecords.length) {
    return positions;
  }

  const localIntervals = new Map();

  for (const { source, target, sourceParticipant, targetParticipant } of endpointRecords) {
    localIntervals.set(
      source,
      getLocalHorizontalInterval(
        source,
        sourceParticipant,
        endpointShapes,
        participantShapes
      )
    );
    localIntervals.set(
      target,
      getLocalHorizontalInterval(
        target,
        targetParticipant,
        endpointShapes,
        participantShapes
      )
    );
  }

  let currentScore = scorePositions(positions);
  let changed;

  do {
    changed = false;

    for (const participant of movableParticipants) {
      const candidates = [ positions.get(participant) ];

      for (const record of endpointRecords) {
        if (record.sourceParticipant === participant) {
          for (const sourceOffset of localIntervals.get(record.source)) {
            for (const targetOffset of localIntervals.get(record.target)) {
              candidates.push(Math.round(
                positions.get(record.targetParticipant) +
                targetOffset -
                sourceOffset
              ));
            }
          }
        } else if (record.targetParticipant === participant) {
          for (const sourceOffset of localIntervals.get(record.source)) {
            for (const targetOffset of localIntervals.get(record.target)) {
              candidates.push(Math.round(
                positions.get(record.sourceParticipant) +
                sourceOffset -
                targetOffset
              ));
            }
          }
        }
      }

      let bestPosition = positions.get(participant);
      let bestScore = currentScore;

      for (const candidate of [ ...new Set(candidates) ]) {
        if (candidate === positions.get(participant)) {
          continue;
        }

        const candidatePositions = new Map(positions);

        candidatePositions.set(participant, candidate);

        const candidateScore = scorePositions(candidatePositions);

        if (compareScores(candidateScore, bestScore) < 0) {
          bestPosition = candidate;
          bestScore = candidateScore;
        }
      }

      if (bestPosition !== positions.get(participant)) {
        positions.set(participant, bestPosition);
        currentScore = bestScore;
        changed = true;
      }
    }
  } while (changed);

  return positions;
}

function alignParticipantComponentsLeft(collaboration, participantShapes) {
  const participants = collaboration.participants || [];

  if (participants.length < 2) {
    return new Map();
  }

  const neighbors = new Map(participants.map(participant => {
    return [ participant, new Set() ];
  }));

  for (const messageFlow of collaboration.messageFlows || []) {
    const source = findEndpointParticipant(messageFlow.sourceRef, collaboration);
    const target = findEndpointParticipant(messageFlow.targetRef, collaboration);

    if (!neighbors.has(source) || !neighbors.has(target) || source === target) {
      continue;
    }

    neighbors.get(source).add(target);
    neighbors.get(target).add(source);
  }

  const targetX = Math.min(...participants.map(participant => {
    return participantShapes.get(participant).x;
  }));
  const offsets = new Map();
  const visited = new Set();

  for (const participant of participants) {
    if (visited.has(participant)) {
      continue;
    }

    const component = [];
    const queue = [ participant ];

    visited.add(participant);

    while (queue.length) {
      const current = queue.shift();

      component.push(current);

      for (const neighbor of neighbors.get(current)) {
        if (!visited.has(neighbor)) {
          visited.add(neighbor);
          queue.push(neighbor);
        }
      }
    }

    const componentX = Math.min(...component.map(member => {
      return participantShapes.get(member).x;
    }));
    const dx = targetX - componentX;

    for (const member of component) {
      offsets.set(member, dx);
    }
  }

  return offsets;
}

function getLocalHorizontalInterval(
    element,
    participant,
    endpointShapes,
    participantShapes) {
  const rect = endpointShapes.get(element);
  const participantBounds = participantShapes.get(participant);

  if (is(element, 'bpmn:Participant')) {
    const inset = ROUTING_MARGIN + MESSAGE_FLOW_SIDE_OFFSET;

    return [ inset, participantBounds.width - inset ];
  }

  return [ rect.x + rect.width / 2 - participantBounds.x ];
}

function horizontalAlignmentScore(
    positions,
    initialPositions,
    collaboration,
    participantShapes,
    endpointShapes,
    connectionRoutes,
    channelOffsets,
    anchorPositionedParticipants,
    expandableParticipants) {
  const translatedShapes = new Map([ ...endpointShapes ].map(([ element, rect ]) => {
    const participant = findEndpointParticipant(element, collaboration);
    const dx = positions.has(participant)
      ? positions.get(participant) - initialPositions.get(participant)
      : 0;

    return [
      element,
      bounds(rect.x + dx, rect.y, rect.width, rect.height)
    ];
  }));
  const translatedParticipants = new Map((collaboration.participants || []).map(participant => {
    return [ participant, translatedShapes.get(participant) ];
  }));

  sizeAndPositionParticipantsFromMessageAnchors(
    collaboration,
    translatedParticipants,
    translatedShapes,
    channelOffsets,
    anchorPositionedParticipants,
    expandableParticipants
  );

  const obstacles = getMessageObstacles(translatedShapes);
  const routes = routeMessageFlows(
    collaboration,
    translatedParticipants,
    translatedShapes,
    obstacles,
    channelOffsets
  );
  const bendCount = [ ...routes.values() ].filter(points => points.length > 2).length;

  const routeCosts = [ ...routes.values() ].map(points => {
    const bendCost = points.length > 2 ? MESSAGE_FLOW_BEND_PENALTY : 0;

    return routeLength(points) + bendCost;
  });
  const routingCost = routeCosts.reduce((total, cost) => total + cost, 0);
  const translatedConnections = connectionRoutes.map(([ element, points ]) => {
    const participant = findEndpointParticipant(element, collaboration);
    const dx = positions.has(participant)
      ? positions.get(participant) - initialPositions.get(participant)
      : 0;

    return points.map(routePoint => point(routePoint.x + dx, routePoint.y));
  });
  const crossingCount = countRouteCrossings([
    ...translatedConnections,
    ...routes.values()
  ]);
  const displacement = [ ...positions ].reduce((total, [ participant, x ]) => {
    return total + Math.abs(x - initialPositions.get(participant));
  }, 0);

  const routeQuality = (collaboration.participants || []).length >
    MAX_EXHAUSTIVE_PARTICIPANT_COUNT
    ? [ bendCount, crossingCount ]
    : [ crossingCount, bendCount ];

  return [
    ...routeQuality,
    Math.max(0, ...routeCosts),
    routingCost,
    displacement
  ];
}

function countRouteCrossings(routes) {
  const segments = routes.map(toSegments);
  let crossings = 0;

  for (let first = 0; first < segments.length; first++) {
    for (let second = first + 1; second < segments.length; second++) {
      for (const [ a, b ] of segments[first]) {
        crossings += segments[second].filter(([ c, d ]) => {
          return segmentsProperlyCross(a, b, c, d);
        }).length;
      }
    }
  }

  return crossings;
}

function getMessageObstacles(shapes) {
  return [ ...shapes ]
    .filter(([ element ]) => {
      return !is(element, 'bpmn:Lane') &&
          !is(element, 'bpmn:Participant') &&
          !is(element, 'bpmn:SubProcess') &&
          !isArtifact(element);
    })
    .map(([ element, rect ]) => ({ element, rect }));
}

function routeMessageFlows(
    collaboration,
    participantShapes,
    endpointShapes,
    obstacles,
    channelOffsets) {
  const routes = new Map();
  const routedConnections = [];

  for (const messageFlow of collaboration.messageFlows || []) {
    const source = resolveMessageFlowEndpoint(messageFlow.sourceRef, endpointShapes);
    const target = resolveMessageFlowEndpoint(messageFlow.targetRef, endpointShapes);
    const sourceBounds = endpointShapes.get(source);
    const targetBounds = endpointShapes.get(target);

    if (!sourceBounds || !targetBounds) {
      continue;
    }

    const points = routeMessageFlow(
      source,
      target,
      sourceBounds,
      targetBounds,
      collaboration,
      participantShapes,
      obstacles,
      routedConnections,
      channelOffsets.get(messageFlow) || 0
    );

    routes.set(messageFlow, points);
    routedConnections.push({ flow: messageFlow, points });
  }

  return routes;
}

function findMessageFlowDockX(
    endpointBounds,
    downward,
    source,
    offset,
    obstacles,
    sourceElement,
    targetElement,
    routedConnections) {
  const inset = Math.min(ROUTING_MARGIN, endpointBounds.width / 2);
  const minX = endpointBounds.x + inset;
  const maxX = endpointBounds.x + endpointBounds.width - inset;
  const centerX = endpointBounds.x + endpointBounds.width / 2;
  const preferredX = Math.max(minX, Math.min(centerX + offset, maxX));
  const candidates = [
    preferredX,
    centerX,
    minX,
    maxX
  ].filter((candidate, index, values) => values.indexOf(candidate) === index);
  const dockY = source === downward
    ? endpointBounds.y + endpointBounds.height
    : endpointBounds.y;
  const outsideY = dockY + (source === downward ? ROUTING_MARGIN : -ROUTING_MARGIN);

  return candidates.find(candidate => {
    return segmentIsClear(
      point(candidate, dockY),
      point(candidate, outsideY),
      obstacles,
      sourceElement,
      targetElement,
      routedConnections,
      MESSAGE_FLOW_OBSTACLE_INSET,
      true
    );
  }) || preferredX;
}

function findMessageFlowVerticalBypass(
    source,
    target,
    sourceBounds,
    targetBounds,
    sourceIsParticipant,
    targetIsParticipant,
    start,
    end,
    obstacles,
    channelOffset) {
  if (sourceIsParticipant === targetIsParticipant) {
    return null;
  }

  const nodeBounds = sourceIsParticipant ? targetBounds : sourceBounds;
  const outgoing = !sourceIsParticipant;
  const participantY = sourceIsParticipant ? start.y : end.y;
  const participantAbove = participantY < nodeBounds.y + nodeBounds.height / 2;
  const dockY = participantAbove ? nodeBounds.y : nodeBounds.y + nodeBounds.height;
  const leadY = dockY + (participantAbove ? -ROUTING_MARGIN : ROUTING_MARGIN);
  const preferredSide = channelOffset || (outgoing ? MESSAGE_FLOW_SIDE_OFFSET : -MESSAGE_FLOW_SIDE_OFFSET);
  const sides = [
    {
      offset: MESSAGE_FLOW_SIDE_OFFSET,
      channelX: nodeBounds.x + nodeBounds.width + ROUTING_MARGIN
    },
    {
      offset: -MESSAGE_FLOW_SIDE_OFFSET,
      channelX: nodeBounds.x - ROUTING_MARGIN
    }
  ].sort((a, b) => {
    return Math.abs(a.offset - preferredSide) - Math.abs(b.offset - preferredSide);
  });

  for (const { channelX } of sides) {
    const dockX = nodeBounds.x + nodeBounds.width / 2 + channelOffset;
    const dock = point(dockX, dockY);
    const lead = point(dockX, leadY);
    const channel = point(channelX, leadY);
    const participantDock = point(channelX, sourceIsParticipant ? start.y : end.y);
    const candidate = sourceIsParticipant
      ? [ participantDock, channel, lead, dock ]
      : [ dock, lead, channel, participantDock ];

    if (pathIsClear(candidate, obstacles, source, target, [])) {
      return candidate;
    }
  }

  return null;
}

function centerIsInside(rect, container) {
  const x = rect.x + rect.width / 2;
  const y = rect.y + rect.height / 2;

  return x >= container.x &&
    x <= container.x + container.width &&
    y >= container.y &&
    y <= container.y + container.height;
}

function routeMessageLeg(start, end, obstacles, source, target, routedConnections) {
  if (segmentIsClear(
    start,
    end,
    obstacles,
    source,
    target,
    routedConnections,
    MESSAGE_FLOW_OBSTACLE_INSET,
    true
  )) {
    return [ start, end ];
  }

  return visibilityRoute(
    start,
    end,
    obstacles,
    source,
    target,
    routedConnections,
    MESSAGE_FLOW_OBSTACLE_INSET,
    true
  ) ||
    visibilityRoute(
      start,
      end,
      obstacles,
      source,
      target,
      [],
      MESSAGE_FLOW_OBSTACLE_INSET,
      true
    ) ||
    [ start, end ];
}

function sizeAndPositionParticipantsFromMessageAnchors(
    collaboration,
    participantShapes,
    endpointShapes,
    channelOffsets,
    anchorPositionedParticipants,
    expandableParticipants) {
  const participants = collaboration.participants || [];
  const anchorPositioned = participants.filter(participant => {
    return anchorPositionedParticipants.has(participant);
  });
  const anchors = new Map(anchorPositioned.map(participant => [ participant, [] ]));
  const participantConnections = [];

  for (const messageFlow of collaboration.messageFlows || []) {
    const source = resolveMessageFlowEndpoint(messageFlow.sourceRef, endpointShapes);
    const target = resolveMessageFlowEndpoint(messageFlow.targetRef, endpointShapes);
    const offset = channelOffsets.get(messageFlow) || 0;

    if (anchors.has(source) && !is(target, 'bpmn:Participant')) {
      const targetBounds = endpointShapes.get(target);

      anchors.get(source).push(targetBounds.x + targetBounds.width / 2 + offset);
    }
    if (anchors.has(target) && !is(source, 'bpmn:Participant')) {
      const sourceBounds = endpointShapes.get(source);

      anchors.get(target).push(sourceBounds.x + sourceBounds.width / 2 + offset);
    }
    if (
      is(source, 'bpmn:Participant') &&
      is(target, 'bpmn:Participant') &&
      (anchors.has(source) || anchors.has(target))
    ) {
      participantConnections.push([ source, target ]);
    }
  }

  const positioned = new Set(participants.filter(participant => {
    return !anchorPositionedParticipants.has(participant);
  }));

  for (const participant of anchorPositioned) {
    const participantAnchors = anchors.get(participant);

    if (!participantAnchors.length) {
      continue;
    }

    const participantBounds = participantShapes.get(participant);
    const anchorsFitCurrentBounds = participantAnchors.every(anchor => {
      return anchor >= participantBounds.x + PARTICIPANT_HEADER_WIDTH &&
        anchor <= participantBounds.x + participantBounds.width - PARTICIPANT_HEADER_WIDTH;
    });

    if (participant.processRef && anchorsFitCurrentBounds) {
      positioned.add(participant);
      continue;
    }

    const min = Math.min(...participantAnchors);
    const max = Math.max(...participantAnchors);
    const width = Math.max(
      participantBounds.width,
      expandableParticipants.has(participant)
        ? max - min + 2 * PARTICIPANT_HEADER_WIDTH
        : 0
    );
    const center = (min + max) / 2;

    participantBounds.x = Math.round(center - width / 2);
    participantBounds.width = Math.round(width);
    positioned.add(participant);
  }

  let changed = true;

  while (changed) {
    changed = false;

    for (const participant of anchorPositioned) {
      if (positioned.has(participant)) {
        continue;
      }

      const neighbors = participantConnections.flatMap(([ source, target ]) => {
        if (source === participant && positioned.has(target)) {
          return [ target ];
        }
        if (target === participant && positioned.has(source)) {
          return [ source ];
        }
        return [];
      });

      if (!neighbors.length) {
        continue;
      }

      const center = neighbors.reduce((sum, neighbor) => {
        const neighborBounds = participantShapes.get(neighbor);

        return sum + neighborBounds.x + neighborBounds.width / 2;
      }, 0) / neighbors.length;
      const participantBounds = participantShapes.get(participant);

      participantBounds.x = Math.round(center - participantBounds.width / 2);
      positioned.add(participant);
      changed = true;
    }
  }

  for (const [ source, target ] of participantConnections) {
    ensureParticipantDockOverlap(
      participantShapes.get(source),
      participantShapes.get(target),
      expandableParticipants.has(source),
      expandableParticipants.has(target)
    );
  }
}

function includeResizableParticipantMessageDocks(
    collaboration,
    participantShapes,
    edges,
    expandableParticipants) {
  let changed = false;

  for (const messageFlow of collaboration.messageFlows || []) {
    const points = edges.get(messageFlow);

    if (!points?.length) {
      continue;
    }

    for (const [ endpoint, dock ] of [
      [ messageFlow.sourceRef, points[0] ],
      [ messageFlow.targetRef, points.at(-1) ]
    ]) {
      if (!is(endpoint, 'bpmn:Participant') || !expandableParticipants.has(endpoint)) {
        continue;
      }

      const participantBounds = participantShapes.get(endpoint);
      const previousX = participantBounds.x;
      const previousWidth = participantBounds.width;

      includeHorizontalRange(
        participantBounds,
        dock.x - PARTICIPANT_HEADER_WIDTH,
        dock.x + PARTICIPANT_HEADER_WIDTH
      );

      changed = changed ||
        participantBounds.x !== previousX ||
        participantBounds.width !== previousWidth;
    }
  }

  return changed;
}

function constrainParticipantDockX(x, participantBounds) {
  return Math.max(
    participantBounds.x + PARTICIPANT_HEADER_WIDTH,
    Math.min(
      x,
      participantBounds.x + participantBounds.width - PARTICIPANT_HEADER_WIDTH
    )
  );
}

function ensureParticipantDockOverlap(
    source,
    target,
    sourceResizable = true,
    targetResizable = true) {
  const sourceRight = source.x + source.width;
  const targetRight = target.x + target.width;
  const overlapStart = Math.max(source.x, target.x);
  const overlapEnd = Math.min(sourceRight, targetRight);

  if (overlapEnd - overlapStart >= 2 * ROUTING_MARGIN) {
    return;
  }

  if (sourceResizable !== targetResizable) {
    const mutable = sourceResizable ? source : target;
    const fixed = sourceResizable ? target : source;
    const fixedRight = fixed.x + fixed.width;
    const mutableCenter = mutable.x + mutable.width / 2;
    const channelX = Math.max(
      fixed.x + ROUTING_MARGIN,
      Math.min(mutableCenter, fixedRight - ROUTING_MARGIN)
    );

    includeHorizontalRange(
      mutable,
      channelX - ROUTING_MARGIN,
      channelX + ROUTING_MARGIN
    );
    return;
  }

  const channelX = Math.round((overlapStart + overlapEnd) / 2);
  const dockStart = channelX - ROUTING_MARGIN;
  const dockEnd = channelX + ROUTING_MARGIN;

  if (sourceResizable) {
    includeHorizontalRange(source, dockStart, dockEnd);
  }
  if (targetResizable) {
    includeHorizontalRange(target, dockStart, dockEnd);
  }
}

function includeHorizontalRange(bounds, min, max) {
  const right = bounds.x + bounds.width;

  bounds.x = Math.min(bounds.x, min);
  bounds.width = Math.max(right, max) - bounds.x;
}

function assignMessageFlowChannelOffsets(collaboration, shapes) {
  const groups = new Map();
  const offsets = new Map();

  for (const messageFlow of collaboration.messageFlows || []) {
    const source = resolveMessageFlowEndpoint(messageFlow.sourceRef, shapes);
    const target = resolveMessageFlowEndpoint(messageFlow.targetRef, shapes);
    const sourceId = source.id;
    const targetId = target.id;
    const key = [ sourceId, targetId ].sort().join(':');

    if (!groups.has(key)) {
      groups.set(key, []);
    }
    groups.get(key).push({ messageFlow, source, target });
  }

  for (const flows of groups.values()) {
    const first = flows[0];
    const forward = flows.filter(flow => {
      return flow.source === first.source && flow.target === first.target;
    });
    const reverse = flows.filter(flow => {
      return flow.source === first.target && flow.target === first.source;
    });

    if (!forward.length || !reverse.length) {
      continue;
    }

    const node = is(first.source, 'bpmn:Participant')
      ? first.target
      : first.source;
    const nodeBounds = shapes.get(node);
    const spacing = nodeBounds && !is(node, 'bpmn:Participant')
      ? Math.min(
        MESSAGE_FLOW_CHANNEL_SPACING,
        Math.floor(nodeBounds.width / MESSAGE_FLOW_CHANNEL_WIDTH_DIVISOR)
      )
      : MESSAGE_FLOW_CHANNEL_SPACING;
    const firstDirection = is(first.source, 'bpmn:Participant') &&
      !is(first.target, 'bpmn:Participant')
      ? 1
      : -1;

    forward.forEach((flow, index) => {
      offsets.set(flow.messageFlow, firstDirection * spacing * (index + 1));
    });
    reverse.forEach((flow, index) => {
      offsets.set(flow.messageFlow, -firstDirection * spacing * (index + 1));
    });
  }

  return offsets;
}

function orderParticipantsByMessageFlow(collaboration, participantShapes, endpointShapes) {
  const participants = collaboration.participants || [];

  if (participants.length < 2) {
    return participants;
  }

  const scoreOrder = createMessageFlowOrderScorer(
    participants,
    collaboration,
    participantShapes,
    endpointShapes
  );

  if (participants.length <= MAX_EXHAUSTIVE_PARTICIPANT_COUNT) {
    let bestOrder = participants;
    let bestScore = null;
    const permute = (prefix, remaining) => {
      if (!remaining.length) {
        const score = scoreOrder(prefix);

        if (!bestScore || compareScores(score, bestScore) < 0) {
          bestOrder = [ ...prefix ];
          bestScore = score;
        }
        return;
      }

      for (let index = 0; index < remaining.length; index++) {
        permute(
          [ ...prefix, remaining[index] ],
          [ ...remaining.slice(0, index), ...remaining.slice(index + 1) ]
        );
      }
    };

    permute([], participants);
    return bestOrder;
  }

  let ordered = [ participants[0] ];

  for (const participant of participants.slice(1)) {
    let bestOrder;
    let bestScore = null;

    for (let index = 0; index <= ordered.length; index++) {
      const candidate = [
        ...ordered.slice(0, index),
        participant,
        ...ordered.slice(index)
      ];
      const score = scoreOrder(candidate);

      if (!bestScore || compareScores(score, bestScore) < 0) {
        bestOrder = candidate;
        bestScore = score;
      }
    }

    ordered = bestOrder;
  }

  let improved;

  do {
    improved = false;

    for (const participant of participants) {
      const currentIndex = ordered.indexOf(participant);
      const remaining = ordered.filter(candidate => candidate !== participant);
      let bestOrder = ordered;
      let bestScore = scoreOrder(ordered);

      for (let index = 0; index <= remaining.length; index++) {
        if (index === currentIndex) {
          continue;
        }

        const candidate = [
          ...remaining.slice(0, index),
          participant,
          ...remaining.slice(index)
        ];
        const score = scoreOrder(candidate);

        if (compareScores(score, bestScore) < 0) {
          bestOrder = candidate;
          bestScore = score;
        }
      }

      if (bestOrder !== ordered) {
        ordered = bestOrder;
        improved = true;
      }
    }
  } while (improved);

  return ordered;
}

function createMessageFlowOrderScorer(
    participants,
    collaboration,
    participantShapes,
    endpointShapes) {
  const context = createMessageFlowOrderContext(collaboration, endpointShapes);

  if (participants.length <= MAX_EXHAUSTIVE_PARTICIPANT_COUNT) {
    return order => messageFlowOrderScore(order, participantShapes, context);
  }

  const participantIndexes = new Map(
    participants.map((participant, index) => [ participant, index ])
  );
  const scores = new Map();

  return order => {
    const key = order.map(participant => participantIndexes.get(participant)).join(':');

    if (!scores.has(key)) {
      scores.set(key, messageFlowOrderScore(order, participantShapes, context));
    }

    return scores.get(key);
  };
}

function createMessageFlowOrderContext(collaboration, endpointShapes) {
  const participantsByProcess = new Map();

  for (const participant of collaboration.participants || []) {
    if (!participantsByProcess.has(participant.processRef)) {
      participantsByProcess.set(participant.processRef, participant);
    }
  }

  const messageFlows = (collaboration.messageFlows || []).map(messageFlow => {
    const source = resolveMessageFlowEndpoint(messageFlow.sourceRef, endpointShapes);
    const target = resolveMessageFlowEndpoint(messageFlow.targetRef, endpointShapes);

    return {
      source,
      target,
      sourceBounds: endpointShapes.get(source),
      targetBounds: endpointShapes.get(target),
      sourceParticipant: findEndpointParticipantCached(source, participantsByProcess),
      targetParticipant: findEndpointParticipantCached(target, participantsByProcess)
    };
  });
  const obstacles = [ ...endpointShapes.entries() ]
    .filter(([ element ]) => {
      return !is(element, 'bpmn:Participant') &&
        !is(element, 'bpmn:Lane') &&
        !is(element, 'bpmn:SubProcess');
    })
    .map(([ element, rect ]) => ({
      element,
      rect,
      participant: findEndpointParticipantCached(element, participantsByProcess),
      insetX: rect.x + MESSAGE_FLOW_OBSTACLE_INSET,
      insetWidth: rect.width - 2 * MESSAGE_FLOW_OBSTACLE_INSET,
      insetHeight: rect.height - 2 * MESSAGE_FLOW_OBSTACLE_INSET
    }));

  return {
    messageFlows,
    obstacles,
    useWeightedSeparation: (collaboration.participants || [])
      .filter(participant => participant.processRef).length > 1,
    prioritizeCollapsedAdjacency: messageFlows.some(({
      sourceParticipant,
      targetParticipant
    }) => {
      return sourceParticipant &&
        targetParticipant &&
        !sourceParticipant.processRef &&
        !targetParticipant.processRef;
    })
  };
}

function messageFlowOrderScore(order, participantShapes, context) {
  const positions = new Map();
  const orderIndex = new Map(order.map((participant, index) => [ participant, index ]));
  let y = 0;

  for (const participant of order) {
    positions.set(participant, y);
    y += participantShapes.get(participant).height + VERTICAL_GAP;
  }

  const geometryScore = context.messageFlows.reduce((total, messageFlow) => {
    const {
      source,
      target,
      sourceParticipant,
      targetParticipant
    } = messageFlow;

    if (!source || !target ||
        !positions.has(sourceParticipant) || !positions.has(targetParticipant)) {
      return total;
    }

    const sourceCenter = getOrderedEndpointCenterY(
      sourceParticipant,
      positions,
      messageFlow.sourceBounds
    );
    const targetCenter = getOrderedEndpointCenterY(
      targetParticipant,
      positions,
      messageFlow.targetBounds
    );
    const downward = targetCenter > sourceCenter;
    const sourceY = getOrderedEndpointDockY(
      sourceParticipant,
      downward,
      true,
      positions,
      messageFlow.sourceBounds
    );
    const targetY = getOrderedEndpointDockY(
      targetParticipant,
      downward,
      false,
      positions,
      messageFlow.targetBounds
    );
    const bendPenalty = orderedMessageFlowNeedsBend(
      messageFlow,
      sourceY,
      targetY,
      positions,
      context.obstacles
    ) ? MESSAGE_FLOW_BEND_PENALTY : 0;

    return total + bendPenalty + Math.abs(targetY - sourceY);
  }, 0);
  let separationPenalty = 0;
  const participantPairs = new Set();

  for (const {
    sourceParticipant: source,
    targetParticipant: target
  } of context.messageFlows) {

    if (!orderIndex.has(source) || !orderIndex.has(target) || source === target) {
      continue;
    }

    const sourceIndex = orderIndex.get(source);
    const targetIndex = orderIndex.get(target);
    if (context.useWeightedSeparation) {
      const first = sourceIndex < targetIndex ? source : target;
      const last = sourceIndex < targetIndex ? target : source;

      if (Math.abs(sourceIndex - targetIndex) > 1) {
        separationPenalty += positions.get(last) -
          positions.get(first) -
          participantShapes.get(first).height -
          VERTICAL_GAP;
      }
    } else if (context.prioritizeCollapsedAdjacency) {
      const pair = sourceIndex < targetIndex
        ? `${ source.id }:${ target.id }`
        : `${ target.id }:${ source.id }`;

      if (!participantPairs.has(pair)) {
        participantPairs.add(pair);
        separationPenalty += Math.max(0, Math.abs(sourceIndex - targetIndex) - 1);
      }
    }
  }

  if (!context.useWeightedSeparation) {
    return context.prioritizeCollapsedAdjacency
      ? [ separationPenalty, geometryScore ]
      : [ geometryScore ];
  }

  return [
    geometryScore + separationPenalty,
    geometryScore,
    separationPenalty
  ];
}

function getOrderedEndpointCenterY(participant, positions, endpointBounds) {
  return positions.get(participant) + endpointBounds.y + endpointBounds.height / 2;
}

function getOrderedEndpointDockY(
    participant,
    downward,
    source,
    positions,
    endpointBounds) {
  const top = positions.get(participant) + endpointBounds.y;
  const dockAtBottom = source ? downward : !downward;

  return dockAtBottom ? top + endpointBounds.height : top;
}

function orderedMessageFlowNeedsBend(
    messageFlow,
    sourceY,
    targetY,
    positions,
    obstacles) {
  const {
    source,
    target,
    sourceParticipant,
    targetParticipant,
    sourceBounds,
    targetBounds
  } = messageFlow;
  const sourceIsParticipant = source === sourceParticipant;
  const targetIsParticipant = target === targetParticipant;
  const sourceX = sourceBounds.x + sourceBounds.width / 2;
  const targetX = targetBounds.x + targetBounds.width / 2;
  const straightX = sourceIsParticipant && !targetIsParticipant
    ? targetX
    : targetIsParticipant && !sourceIsParticipant
      ? sourceX
      : sourceIsParticipant && targetIsParticipant
        ? sourceX
        : sourceX === targetX
          ? sourceX
          : null;

  if (straightX === null) {
    return true;
  }

  const start = point(straightX, sourceY);
  const end = point(straightX, targetY);
  const minY = Math.min(start.y, end.y);
  const maxY = Math.max(start.y, end.y);

  for (const {
    element,
    participant,
    insetX,
    insetWidth,
    insetHeight,
    rect
  } of obstacles) {
    if (element === source || element === target) {
      continue;
    }

    if (!positions.has(participant)) {
      continue;
    }

    const obstacle = bounds(
      insetX,
      rect.y + positions.get(participant) + MESSAGE_FLOW_OBSTACLE_INSET,
      insetWidth,
      insetHeight
    );
    const verticallyDisjoint = minY === maxY
      ? minY < obstacle.y || minY > obstacle.y + obstacle.height
      : maxY <= obstacle.y || minY >= obstacle.y + obstacle.height;

    if (start.x < obstacle.x ||
        start.x > obstacle.x + obstacle.width ||
        verticallyDisjoint) {
      continue;
    }

    if (segmentEntersRect(start, end, obstacle)) {
      return true;
    }
  }

  return false;
}

function findEndpointParticipant(endpoint, collaboration) {
  if (is(endpoint, 'bpmn:Participant')) {
    return endpoint;
  }

  const process = findEndpointProcess(endpoint);

  return (collaboration.participants || [])
    .find(participant => participant.processRef === process);
}

function findEndpointParticipantCached(endpoint, participantsByProcess) {
  if (is(endpoint, 'bpmn:Participant')) {
    return endpoint;
  }

  return participantsByProcess.get(findEndpointProcess(endpoint));
}

function findEndpointProcess(endpoint) {
  let parent = endpoint?.$parent;

  while (parent && !is(parent, 'bpmn:Process')) {
    parent = parent.$parent;
  }

  return parent;
}

function resolveMessageFlowEndpoint(endpoint, shapes) {
  let visibleEndpoint = endpoint;

  while (visibleEndpoint && !shapes.has(visibleEndpoint)) {
    visibleEndpoint = visibleEndpoint.$parent;
  }

  return visibleEndpoint;
}

function layoutExternalLabels(factory, planeElements) {
  const shapes = planeElements
    .filter(element => element.$instanceOf('bpmndi:BPMNShape') && element.bounds)
    .map((di, index) => ({
      di,
      element: di.bpmnElement,
      rect: di.bounds,
      index,
      isContainer: isContainer(di),
      titleBounds: expandedSubProcessTitleBounds(
        di.bpmnElement,
        di.bounds,
        di.isExpanded === true
      )
    }));
  const edges = planeElements
    .filter(element => element.$instanceOf('bpmndi:BPMNEdge') && element.waypoint?.length > 1)
    .map((di, index) => ({
      di,
      element: di.bpmnElement,
      points: di.waypoint,
      index
    }));
  const edgeSegments = flattenEdgeSegments(edges);
  const shapeByElement = new Map(shapes.map(shape => [ shape.element, shape ]));
  const labels = [
    ...shapes.map(shape => labelRecord(shape, shape.index)),
    ...edges.map(edge => labelRecord(edge, shapes.length + edge.index))
  ].filter(Boolean);
  const occupied = [];
  const preferredByLabel = new Map(labels.map(label => {
    return [ label, preferredCandidates(label, shapeByElement, edges) ];
  }));
  const staticCandidateCountByLabel = new Map(labels.map(label => {
    const count = preferredByLabel.get(label)
      .filter(candidate => candidateIsClear(candidate, label, shapes, edgeSegments, []))
      .length;

    return [ label, count ];
  }));

  labels.sort((a, b) => {
    return staticCandidateCountByLabel.get(a) - staticCandidateCountByLabel.get(b) ||
      b.size.width * b.size.height - a.size.width * a.size.height ||
      a.index - b.index;
  });

  for (const label of labels) {
    const preferred = preferredByLabel.get(label);
    const defaultCandidate = preferred[0];
    const candidate = preferred.find(rect => {
      return candidateIsClear(rect, label, shapes, edgeSegments, occupied);
    }) || freeCandidate(label, defaultCandidate, shapes, edgeSegments, occupied);

    if (!candidate) {
      throw new LayoutError(
        'LABEL_PLACEMENT_FAILED',
        label.element.id,
        `No collision-free external label position could be found (${label.element.id}).`
      );
    }

    label.di.label = factory.createDiLabel(integerBounds(candidate));
    occupied.push(candidate);
  }
}

function needsExpandedSubProcessTitleClearance(element, rect, childLayout) {
  const expandedElements = collectExpandedElements(childLayout);
  const shapes = [
    ...childLayout.shapes.entries(),
    ...getExpandedChildShapes(childLayout)
  ].map(([ childElement, childRect ], index) => {
    const expanded = expandedElements.has(childElement);

    return {
      element: childElement,
      rect: childRect,
      index,
      isContainer: expanded ||
        is(childElement, 'bpmn:Participant') ||
        is(childElement, 'bpmn:Lane'),
      titleBounds: expandedSubProcessTitleBounds(
        childElement,
        childRect,
        expanded
      )
    };
  });
  const container = {
    element,
    rect,
    isContainer: true,
    titleBounds: expandedSubProcessTitleBounds(element, rect, true)
  };
  const allShapes = [ container, ...shapes ];
  const edges = [
    ...childLayout.edges.entries(),
    ...getExpandedChildEdges(childLayout)
  ].map(([ childElement, points ], index) => ({
    element: childElement,
    points,
    index
  }));
  const edgeSegments = flattenEdgeSegments(edges);
  const shapeByElement = new Map(shapes.map(shape => [ shape.element, shape ]));
  const labels = [
    ...shapes.map(shape => labelRecord(shape, shape.index)),
    ...edges.map(edge => labelRecord(edge, shapes.length + edge.index))
  ].filter(Boolean);

  return labels.some(label => {
    const preferred = preferredCandidates(label, shapeByElement, edges);
    const hasClearPreferred = preferred.some(candidate => {
      return candidateIsClear(candidate, label, allShapes, edgeSegments, []);
    });
    const hasTitleBlockedPreferred = preferred.some(candidate => {
      return overlapsAnyContainerTitle(candidate, allShapes) &&
        candidateIsCollisionFree(
          candidate,
          allShapes,
          edgeSegments,
          [],
          true
        ) &&
        hasClearOwnerCorridor(candidate, label, allShapes);
    });

    return !hasClearPreferred && hasTitleBlockedPreferred;
  });
}

function collectExpandedElements(layout) {
  const elements = new Set();

  for (const child of layout.children) {
    if (!child.emitInParent) {
      continue;
    }

    elements.add(child.scope);

    for (const element of collectExpandedElements(child)) {
      elements.add(element);
    }
  }

  return elements;
}

function labelRecord(owner, index) {
  const element = owner.element;
  const text = isExternalLabelOwner(element)
    ? getExternalLabelText(element).trim()
    : '';

  if (!text) {
    return null;
  }

  return {
    ...owner,
    index,
    text,
    size: externalLabelSize(text)
  };
}

function externalLabelSize(text) {
  const availableWidth = EXTERNAL_LABEL_WIDTH -
    2 * EXTERNAL_LABEL_HORIZONTAL_PADDING;
  let lineCount = 0;
  let maxLineWidth = 0;

  for (const paragraph of text.trim().split(/\n/)) {
    const words = paragraph.trim().split(/\s+/).filter(Boolean);
    let lineWidth = 0;
    let paragraphLines = 1;

    for (const word of words) {
      const chunks = wordChunks(word);

      for (let index = 0; index < chunks.length; index++) {
        const chunkWidth = chunks[index];
        const separator = lineWidth && index === 0
          ? EXTERNAL_LABEL_SPACE_WIDTH
          : 0;

        if (!lineWidth) {
          lineWidth = chunkWidth;
        } else if (index === 0 &&
            lineWidth + separator + chunkWidth <= availableWidth) {
          lineWidth += separator + chunkWidth;
        } else {
          maxLineWidth = Math.max(maxLineWidth, lineWidth);
          paragraphLines++;
          lineWidth = chunkWidth;
        }
      }
    }

    lineCount += paragraphLines;
    maxLineWidth = Math.max(maxLineWidth, lineWidth);
  }

  return {
    width: Math.min(
      EXTERNAL_LABEL_WIDTH,
      maxLineWidth + 2 * EXTERNAL_LABEL_HORIZONTAL_PADDING
    ),
    height: Math.max(
      EXTERNAL_LABEL_LINE_HEIGHT,
      lineCount * EXTERNAL_LABEL_LINE_HEIGHT
    )
  };
}

function wordChunks(word) {
  const chunks = [];
  let width = 0;

  for (const character of word) {
    const characterWidth = externalCharacterWidth(character);

    if (width && width + characterWidth >
      EXTERNAL_LABEL_WIDTH - 2 * EXTERNAL_LABEL_HORIZONTAL_PADDING) {
      chunks.push(width);
      width = characterWidth;
    } else {
      width += characterWidth;
    }
  }

  if (width) {
    chunks.push(width);
  }

  return chunks;
}

function externalCharacterWidth(character) {
  if (/[MW]/.test(character)) {
    return EXTERNAL_LABEL_WIDE_CHARACTER_WIDTH;
  }
  if (/[mw]/.test(character)) {
    return EXTERNAL_LABEL_WIDE_CHARACTER_WIDTH - 1;
  }
  if (/[A-Z]/.test(character)) {
    return EXTERNAL_LABEL_UPPERCASE_WIDTH;
  }

  return EXTERNAL_LABEL_CHARACTER_WIDTH;
}

function preferredCandidates(label, shapeByElement, edges) {
  if (label.points) {
    return connectionLabelCandidates(label, edges);
  }

  const owner = shapeByElement.get(label.element);

  if (!owner) {
    return [];
  }

  const candidates = shapeLabelCandidates(owner.rect, label.size);

  return is(label.element, 'bpmn:Group')
    ? [ candidates[1], candidates[0], candidates[2], candidates[3] ]
    : candidates;
}

function shapeLabelCandidates(owner, size) {
  const centerX = owner.x + owner.width / 2;
  const centerY = owner.y + owner.height / 2;

  return [
    bounds(
      centerX - size.width / 2,
      owner.y + owner.height + EXTERNAL_LABEL_CLEARANCE,
      size.width,
      size.height
    ),
    bounds(
      centerX - size.width / 2,
      owner.y - EXTERNAL_LABEL_CLEARANCE - size.height,
      size.width,
      size.height
    ),
    bounds(
      owner.x - EXTERNAL_LABEL_CLEARANCE - size.width,
      centerY - size.height / 2,
      size.width,
      size.height
    ),
    bounds(
      owner.x + owner.width + EXTERNAL_LABEL_CLEARANCE,
      centerY - size.height / 2,
      size.width,
      size.height
    )
  ];
}

function connectionLabelCandidates(label, edges) {
  const segments = label.points.slice(0, -1).map((start, index) => ({
    start,
    end: label.points[index + 1],
    index
  })).filter(({ start, end }) => start.x !== end.x || start.y !== end.y);
  const middle = (segments.length - 1) / 2;
  const partitioned = segments.map(segment => {
    return partitionConnectionSegment(segment, label.element, edges);
  });
  const unique = partitioned.flatMap(parts => parts.unique);
  const shared = partitioned.flatMap(parts => parts.shared);

  const compare = (a, b) => {
    return Math.abs(a.index - middle) - Math.abs(b.index - middle) ||
      a.index - b.index ||
      a.centerOffset - b.centerOffset;
  };

  return [ ...unique.sort(compare), ...shared.sort(compare) ].flatMap(segment => {
    return segmentLabelCandidates(segment.start, segment.end, label.size);
  });
}

function partitionConnectionSegment(segment, owner, edges) {
  const horizontal = segment.start.y === segment.end.y;
  const vertical = segment.start.x === segment.end.x;

  if (!horizontal && !vertical) {
    return {
      unique: [ segmentPart(segment, segment.start, segment.end) ],
      shared: []
    };
  }

  const start = horizontal
    ? Math.min(segment.start.x, segment.end.x)
    : Math.min(segment.start.y, segment.end.y);
  const end = horizontal
    ? Math.max(segment.start.x, segment.end.x)
    : Math.max(segment.start.y, segment.end.y);
  const overlaps = [];

  for (const edge of edges) {
    if (edge.element === owner) {
      continue;
    }

    for (let index = 0; index < edge.points.length - 1; index++) {
      const otherStart = edge.points[index];
      const otherEnd = edge.points[index + 1];
      const collinear = horizontal
        ? otherStart.y === otherEnd.y && otherStart.y === segment.start.y
        : otherStart.x === otherEnd.x && otherStart.x === segment.start.x;

      if (!collinear) {
        continue;
      }

      const otherMin = horizontal
        ? Math.min(otherStart.x, otherEnd.x)
        : Math.min(otherStart.y, otherEnd.y);
      const otherMax = horizontal
        ? Math.max(otherStart.x, otherEnd.x)
        : Math.max(otherStart.y, otherEnd.y);
      const overlapStart = Math.max(start, otherMin);
      const overlapEnd = Math.min(end, otherMax);

      if (overlapEnd > overlapStart) {
        overlaps.push([ overlapStart, overlapEnd ]);
      }
    }
  }

  const merged = mergeIntervals(overlaps);
  const unique = [];
  const shared = [];
  let cursor = start;

  for (const [ overlapStart, overlapEnd ] of merged) {
    if (overlapStart > cursor) {
      unique.push(toSegmentPart(segment, cursor, overlapStart, horizontal));
    }

    shared.push(toSegmentPart(segment, overlapStart, overlapEnd, horizontal));
    cursor = overlapEnd;
  }

  if (cursor < end) {
    unique.push(toSegmentPart(segment, cursor, end, horizontal));
  }

  return { unique, shared };
}

function mergeIntervals(intervals) {
  const merged = [];

  for (const interval of intervals.sort((a, b) => a[0] - b[0] || a[1] - b[1])) {
    const previous = merged.at(-1);

    if (previous && interval[0] <= previous[1]) {
      previous[1] = Math.max(previous[1], interval[1]);
    } else {
      merged.push([ ...interval ]);
    }
  }

  return merged;
}

function toSegmentPart(segment, start, end, horizontal) {
  return segmentPart(
    segment,
    horizontal
      ? { x: start, y: segment.start.y }
      : { x: segment.start.x, y: start },
    horizontal
      ? { x: end, y: segment.start.y }
      : { x: segment.start.x, y: end }
  );
}

function segmentPart(segment, start, end) {
  const originalCenter = {
    x: (segment.start.x + segment.end.x) / 2,
    y: (segment.start.y + segment.end.y) / 2
  };
  const center = {
    x: (start.x + end.x) / 2,
    y: (start.y + end.y) / 2
  };

  return {
    start,
    end,
    index: segment.index,
    centerOffset: Math.hypot(
      center.x - originalCenter.x,
      center.y - originalCenter.y
    )
  };
}

function segmentLabelCandidates(start, end, size) {
  const horizontal = start.y === end.y;
  const vertical = start.x === end.x;
  const length = Math.hypot(end.x - start.x, end.y - start.y);
  const offsets = [ 0 ];

  for (let offset = ROUTING_MARGIN; offset <= length / 2; offset += ROUTING_MARGIN) {
    offsets.push(offset, -offset);
  }

  if (horizontal) {
    const centerX = (start.x + end.x) / 2;
    const gap = Math.max(
      EXTERNAL_LABEL_CLEARANCE,
      FLOW_LABEL_INDENT - size.height / 2
    );

    return offsets.flatMap(offset => [
      bounds(
        centerX + offset - size.width / 2,
        start.y - gap - size.height,
        size.width,
        size.height
      ),
      bounds(
        centerX + offset - size.width / 2,
        start.y + gap,
        size.width,
        size.height
      )
    ]);
  }

  if (vertical) {
    const centerY = (start.y + end.y) / 2;
    const gap = Math.max(
      EXTERNAL_LABEL_CLEARANCE,
      FLOW_LABEL_INDENT - size.width / 2
    );

    return offsets.flatMap(offset => [
      bounds(
        start.x + gap,
        centerY + offset - size.height / 2,
        size.width,
        size.height
      ),
      bounds(
        start.x - gap - size.width,
        centerY + offset - size.height / 2,
        size.width,
        size.height
      )
    ]);
  }

  const midpoint = {
    x: (start.x + end.x) / 2,
    y: (start.y + end.y) / 2
  };
  const normal = {
    x: -(end.y - start.y) / length,
    y: (end.x - start.x) / length
  };
  const distance = EXTERNAL_LABEL_CLEARANCE +
    Math.hypot(size.width, size.height) / 2;

  return [ 1, -1 ].map(direction => bounds(
    midpoint.x + normal.x * distance * direction - size.width / 2,
    midpoint.y + normal.y * distance * direction - size.height / 2,
    size.width,
    size.height
  ));
}

function freeCandidate(label, preferred, shapes, edgeSegments, occupied) {
  if (!preferred) {
    return null;
  }

  const candidates = [];
  let detachedFallback = false;

  for (let step = 1; step <= MAX_LABEL_SEARCH_STEPS; step++) {
    for (let dx = -step; dx <= step; dx++) {
      const dy = step - Math.abs(dx);
      const offsets = dy ? [ dy, -dy ] : [ 0 ];

      for (const offsetY of offsets) {
        const candidate = {
          rect: bounds(
            preferred.x + dx * ROUTING_MARGIN,
            preferred.y + offsetY * ROUTING_MARGIN,
            label.size.width,
            label.size.height
          ),
          displacement: step,
          index: candidates.length
        };

        candidates.push(candidate);

        if (!detachedFallback &&
            candidateIsCollisionFree(candidate.rect, shapes, edgeSegments, occupied)) {
          if (hasClearOwnerCorridor(candidate.rect, label, shapes)) {
            return candidate.rect;
          }

          detachedFallback = true;
        }
      }
    }
  }

  if (!detachedFallback) {
    return null;
  }

  for (const candidate of candidates) {
    candidate.ownerDistance = ownerAttachment(label, candidate.rect).distance;
  }

  candidates.sort((a, b) => {
    return a.ownerDistance - b.ownerDistance ||
      a.displacement - b.displacement ||
      a.index - b.index;
  });

  return candidates
    .map(candidate => candidate.rect)
    .find(candidate => {
      return candidateIsClear(candidate, label, shapes, edgeSegments, occupied);
    }) || null;
}

function candidateIsClear(candidate, label, shapes, edgeSegments, occupied) {
  return candidateIsCollisionFree(candidate, shapes, edgeSegments, occupied) &&
    hasClearOwnerCorridor(candidate, label, shapes);
}

function candidateIsCollisionFree(
    candidate,
    shapes,
    edgeSegments,
    occupied,
    ignoreContainerTitles = false) {
  const footprint = expand(candidate, EXTERNAL_LABEL_CLEARANCE);
  const edgeFootprint = expand(candidate, EXTERNAL_LABEL_CLEARANCE - 1);

  if (occupied.some(label => rectanglesOverlap(footprint, expand(label, EXTERNAL_LABEL_CLEARANCE)))) {
    return false;
  }

  for (const shape of shapes) {
    if (shape.isContainer) {
      if (straddles(candidate, shape.rect) ||
          overlapsParticipantHeader(candidate, shape) ||
          (!ignoreContainerTitles && overlapsContainerTitle(footprint, shape))) {
        return false;
      }
    } else if (rectanglesOverlap(footprint, shape.rect)) {
      return false;
    }
  }

  const edgeFootprintMaxX = edgeFootprint.x + edgeFootprint.width;
  const edgeFootprintMaxY = edgeFootprint.y + edgeFootprint.height;

  for (const segment of edgeSegments) {
    if (segment.maxX < edgeFootprint.x ||
        segment.minX > edgeFootprintMaxX ||
        segment.maxY < edgeFootprint.y ||
        segment.minY > edgeFootprintMaxY) {
      continue;
    }

    if (segmentEntersRect(segment.start, segment.end, edgeFootprint)) {
      return false;
    }
  }

  return true;
}

function flattenEdgeSegments(edges) {
  const segments = [];

  for (const edge of edges) {
    for (let index = 0; index < edge.points.length - 1; index++) {
      const start = edge.points[index];
      const end = edge.points[index + 1];

      segments.push({
        start,
        end,
        minX: Math.min(start.x, end.x),
        maxX: Math.max(start.x, end.x),
        minY: Math.min(start.y, end.y),
        maxY: Math.max(start.y, end.y)
      });
    }
  }

  return segments;
}

function overlapsAnyContainerTitle(candidate, shapes) {
  const footprint = expand(candidate, EXTERNAL_LABEL_CLEARANCE);

  return shapes.some(shape => {
    return shape.isContainer && overlapsContainerTitle(footprint, shape);
  });
}

function hasClearOwnerCorridor(candidate, label, shapes) {
  const attachment = ownerAttachment(label, candidate);

  return !shapes.some(shape => {
    return !shape.isContainer &&
      shape.element !== label.element &&
      segmentEntersRect(
        attachment.labelPoint,
        attachment.ownerPoint,
        shape.rect
      );
  });
}

function ownerAttachment(label, candidate) {
  if (label.rect) {
    return rectangleAttachment(candidate, label.rect);
  }

  return polylineAttachment(candidate, label.points);
}

function rectangleAttachment(candidate, owner) {
  const [ labelX, ownerX ] = closestAxisPoints(
    candidate.x,
    candidate.x + candidate.width,
    owner.x,
    owner.x + owner.width
  );
  const [ labelY, ownerY ] = closestAxisPoints(
    candidate.y,
    candidate.y + candidate.height,
    owner.y,
    owner.y + owner.height
  );

  return attachment(
    { x: labelX, y: labelY },
    { x: ownerX, y: ownerY }
  );
}

function closestAxisPoints(aStart, aEnd, bStart, bEnd) {
  if (aEnd < bStart) {
    return [ aEnd, bStart ];
  }
  if (bEnd < aStart) {
    return [ aStart, bEnd ];
  }

  const shared = (Math.max(aStart, bStart) + Math.min(aEnd, bEnd)) / 2;

  return [ shared, shared ];
}

function polylineAttachment(candidate, points) {
  let closest = null;

  for (let index = 0; index < points.length - 1; index++) {
    const current = segmentRectangleAttachment(
      candidate,
      points[index],
      points[index + 1]
    );

    if (!closest || current.distance < closest.distance) {
      closest = current;
    }
  }

  return closest;
}

function segmentRectangleAttachment(rect, start, end) {
  const corners = [
    { x: rect.x, y: rect.y },
    { x: rect.x + rect.width, y: rect.y },
    { x: rect.x + rect.width, y: rect.y + rect.height },
    { x: rect.x, y: rect.y + rect.height }
  ];
  const candidates = corners.map(labelPoint => {
    return attachment(labelPoint, closestPointOnSegment(labelPoint, start, end));
  });

  for (const ownerPoint of [ start, end ]) {
    candidates.push(attachment(
      closestPointInRectangle(ownerPoint, rect),
      ownerPoint
    ));
  }

  return candidates.reduce((closest, current) => {
    return current.distance < closest.distance ? current : closest;
  });
}

function closestPointOnSegment(candidate, start, end) {
  const dx = end.x - start.x;
  const dy = end.y - start.y;
  const lengthSquared = dx * dx + dy * dy;
  const ratio = lengthSquared
    ? Math.max(0, Math.min(1,
      ((candidate.x - start.x) * dx + (candidate.y - start.y) * dy) /
        lengthSquared
    ))
    : 0;

  return {
    x: start.x + ratio * dx,
    y: start.y + ratio * dy
  };
}

function closestPointInRectangle(candidate, rect) {
  return {
    x: Math.max(rect.x, Math.min(candidate.x, rect.x + rect.width)),
    y: Math.max(rect.y, Math.min(candidate.y, rect.y + rect.height))
  };
}

function attachment(labelPoint, ownerPoint) {
  return {
    labelPoint,
    ownerPoint,
    distance: Math.hypot(
      ownerPoint.x - labelPoint.x,
      ownerPoint.y - labelPoint.y
    )
  };
}

function isContainer(di) {
  return di.isExpanded === true ||
    is(di.bpmnElement, 'bpmn:Participant') ||
    is(di.bpmnElement, 'bpmn:Lane') ||
    is(di.bpmnElement, 'bpmn:Group');
}

function straddles(candidate, container) {
  return rectanglesOverlap(candidate, container) &&
    !contains(container, candidate);
}

function contains(container, candidate) {
  return candidate.x >= container.x &&
    candidate.y >= container.y &&
    candidate.x + candidate.width <= container.x + container.width &&
    candidate.y + candidate.height <= container.y + container.height;
}

function overlapsParticipantHeader(candidate, shape) {
  if (!is(shape.element, 'bpmn:Participant')) {
    return false;
  }

  return rectanglesOverlap(candidate, bounds(
    shape.rect.x,
    shape.rect.y,
    PARTICIPANT_HEADER_WIDTH,
    shape.rect.height
  ));
}

function overlapsContainerTitle(candidate, shape) {
  if (!shape.titleBounds) {
    return false;
  }

  return rectanglesOverlap(candidate, shape.titleBounds);
}

function expandedSubProcessTitleBounds(element, rect, expanded) {
  if (!expanded || !hasSubProcessLabel(element)) {
    return null;
  }

  const maximumWidth = Math.max(
    0,
    rect.width - 2 * EXPANDED_SUBPROCESS_LABEL_PADDING
  );
  const textWidth = Math.max(...element.name.split('\n').map(line => {
    return [ ...line ].reduce((width, character) => {
      return width + (character === ' '
        ? EXTERNAL_LABEL_SPACE_WIDTH
        : externalCharacterWidth(character));
    }, 0);
  }));
  const width = Math.min(
    maximumWidth,
    textWidth + 2 * EXPANDED_SUBPROCESS_LABEL_PADDING
  );

  return bounds(
    rect.x + (rect.width - width) / 2,
    rect.y,
    width,
    EXPANDED_SUBPROCESS_LABEL_HEIGHT
  );
}

function expand(rect, margin) {
  return bounds(
    rect.x - margin,
    rect.y - margin,
    rect.width + 2 * margin,
    rect.height + 2 * margin
  );
}

function layoutGroups(groups, layout) {
  const warnings = [];
  const shapes = [
    ...layout.shapes.entries(),
    ...getExpandedChildShapes(layout)
  ];
  const edges = [
    ...layout.edges.entries(),
    ...getExpandedChildEdges(layout)
  ];

  for (const group of groups) {
    const categoryValue = group.categoryValueRef;
    const memberShapes = shapes.filter(([ element ]) => {
      return referencesCategoryValue(element, categoryValue);
    });
    const memberEdges = edges.filter(([ element ]) => {
      return referencesCategoryValue(element, categoryValue);
    });
    const points = [
      ...memberShapes.flatMap(([ , rect ]) => [
        { x: rect.x, y: rect.y },
        { x: rect.x + rect.width, y: rect.y + rect.height }
      ]),
      ...memberEdges.flatMap(([ , waypoints ]) => waypoints)
    ];

    if (!points.length) {
      warnings.push(new LayoutWarning(
        'GROUP_MEMBERS_NOT_FOUND',
        group.id,
        `Group ${ group.id } has no visible explicitly referenced members and was omitted.`
      ));
      continue;
    }

    const minX = Math.min(...points.map(point => point.x));
    const minY = Math.min(...points.map(point => point.y));
    const maxX = Math.max(...points.map(point => point.x));
    const maxY = Math.max(...points.map(point => point.y));

    layout.shapes.set(group, bounds(
      minX - GROUP_PADDING,
      minY - GROUP_PADDING,
      maxX - minX + 2 * GROUP_PADDING,
      maxY - minY + 2 * GROUP_PADDING
    ));
  }

  return warnings;
}

function referencesCategoryValue(element, categoryValue) {
  if (!categoryValue) {
    return false;
  }

  const references = Array.isArray(element.categoryValueRef)
    ? element.categoryValueRef
    : [];

  return references.some(reference => {
    return reference === categoryValue ||
      reference.id === categoryValue.id;
  });
}

function emitLayout(factory, layout, planeElements) {
  for (const [ element, rect ] of layout.shapes) {
    const attrs = {};

    if (is(element, 'bpmn:SubProcess') && layout.children.some(child => child.scope === element && child.emitInParent)) {
      attrs.isExpanded = true;
    }

    if (is(element, 'bpmn:Participant') || is(element, 'bpmn:Lane')) {
      attrs.isHorizontal = true;
    }

    if (is(element, 'bpmn:Gateway')) {
      attrs.isMarkerVisible = true;
    }

    planeElements.push(factory.createDiShape(element, integerBounds(rect), {
      id: `BPMNShape_${element.id}`,
      ...attrs
    }));
  }

  const emittedShapes = new Map([
    ...layout.shapes,
    ...getExpandedChildShapes(layout)
  ]);

  for (const [ element, points ] of layout.edges) {
    const routedConnections = [ ...layout.edges ]
      .filter(([ candidate ]) => candidate !== element)
      .map(([ flow, candidatePoints ]) => ({ flow, points: candidatePoints }));

    planeElements.push(factory.createDiEdge(element, orientDockingPoints(
      element,
      cleanPoints(points),
      emittedShapes,
      routedConnections
    ), {
      id: `BPMNEdge_${element.id}`
    }));
  }

  for (const child of layout.children) {
    if (child.emitInParent) {
      emitLayout(factory, child, planeElements);
    }
  }
}

function orientDockingPoints(element, points, shapes, routedConnections) {
  if (is(element, 'bpmn:Association') || is(element, 'bpmn:DataAssociation')) {
    return points;
  }

  const source = Array.isArray(element.sourceRef) ? element.sourceRef[0] : element.sourceRef;
  const sourceBounds = shapes.get(source);
  const targetBounds = shapes.get(element.targetRef);
  const boundaryRoute = sourceBounds && targetBounds
    ? enforceBoundaryVerticalExit(
      element,
      source,
      points,
      sourceBounds,
      targetBounds,
      shapes,
      routedConnections
    )
    : points;
  const elbowRoute = sourceBounds && targetBounds
    ? flipTangentElbow(
      element,
      source,
      boundaryRoute,
      sourceBounds,
      targetBounds,
      shapes
    )
    : boundaryRoute;
  const route = sourceBounds && targetBounds
    ? centerOrthogonalElbow(
      element,
      source,
      elbowRoute,
      sourceBounds,
      targetBounds,
      shapes,
      routedConnections
    )
    : elbowRoute;
  const oriented = route.map(({ x, y }) => point(x, y));

  if (sourceBounds) {
    orientPlaneEdgeDocking(oriented, sourceBounds, true, true);
  }
  if (targetBounds) {
    orientPlaneEdgeDocking(oriented, targetBounds, false, true);
  }

  return cleanPoints(oriented);
}

function enforceBoundaryVerticalExit(
    element,
    sourceElement,
    points,
    sourceBounds,
    targetBounds,
    shapes,
    routedConnections) {
  if (!is(sourceElement, 'bpmn:BoundaryEvent') || points.length < 2) {
    return points;
  }

  const host = shapes.get(sourceElement.attachedToRef);
  const sourceCenterY = sourceBounds.y + sourceBounds.height / 2;
  const sourceTop = host &&
    Math.abs(sourceCenterY - host.y) <
    Math.abs(sourceCenterY - (host.y + host.height));
  const sourceDock = point(
    sourceBounds.x + sourceBounds.width / 2,
    sourceTop ? sourceBounds.y : sourceBounds.y + sourceBounds.height
  );
  const direction = sourceTop ? -1 : 1;

  if (
    points[0].x === sourceDock.x &&
    points[0].y === sourceDock.y &&
    points[1].x === sourceDock.x &&
    Math.sign(points[1].y - sourceDock.y) === direction
  ) {
    return points;
  }

  const stub = point(sourceDock.x, sourceDock.y + direction * ROUTING_MARGIN);
  const targetDock = facingDock(sourceDock, targetBounds);
  const obstacles = connectionObstacles(shapes);
  const targetDocks = [
    targetDock,
    oppositeDock(targetDock, targetBounds)
  ];
  let fallback = null;

  for (const candidateDock of targetDocks) {
    const targetStub = outwardDockingStub(candidateDock, targetBounds);
    const route = visibilityRoute(
      stub,
      targetStub,
      obstacles,
      sourceElement,
      element.targetRef,
      routedConnections
    ) || visibilityRoute(
      stub,
      targetStub,
      obstacles,
      sourceElement,
      element.targetRef,
      []
    );

    if (!route) {
      continue;
    }

    const candidate = cleanPoints([ sourceDock, ...route, candidateDock ]);

    fallback ||= candidate;

    if (!hasUTurn(candidate)) {
      return candidate;
    }
  }

  return fallback || points;
}

function outwardDockingStub(dock, rect) {
  if (dock.x === rect.x) {
    return point(dock.x - ROUTING_MARGIN, dock.y);
  }
  if (dock.x === rect.x + rect.width) {
    return point(dock.x + ROUTING_MARGIN, dock.y);
  }
  if (dock.y === rect.y) {
    return point(dock.x, dock.y - ROUTING_MARGIN);
  }

  return point(dock.x, dock.y + ROUTING_MARGIN);
}

function oppositeDock(dock, rect) {
  if (dock.x === rect.x) {
    return point(rect.x + rect.width, rect.y + rect.height / 2);
  }
  if (dock.x === rect.x + rect.width) {
    return point(rect.x, rect.y + rect.height / 2);
  }
  if (dock.y === rect.y) {
    return point(rect.x + rect.width / 2, rect.y + rect.height);
  }

  return point(rect.x + rect.width / 2, rect.y);
}

function hasUTurn(points) {
  for (let index = 1; index < points.length - 1; index++) {
    const incomingX = points[index].x - points[index - 1].x;
    const incomingY = points[index].y - points[index - 1].y;
    const outgoingX = points[index + 1].x - points[index].x;
    const outgoingY = points[index + 1].y - points[index].y;
    const cross = incomingX * outgoingY - incomingY * outgoingX;
    const dot = incomingX * outgoingX + incomingY * outgoingY;

    if (cross === 0 && dot < 0) {
      return true;
    }
  }

  return false;
}

function facingDock(source, rect) {
  if (source.x < rect.x) {
    return point(rect.x, rect.y + rect.height / 2);
  }
  if (source.x > rect.x + rect.width) {
    return point(rect.x + rect.width, rect.y + rect.height / 2);
  }
  if (source.y < rect.y) {
    return point(rect.x + rect.width / 2, rect.y);
  }
  return point(rect.x + rect.width / 2, rect.y + rect.height);
}

function centerOrthogonalElbow(
    element,
    sourceElement,
    points,
    sourceBounds,
    targetBounds,
    shapes,
    routedConnections) {
  if (!is(element, 'bpmn:SequenceFlow') || points.length !== 3) {
    return points;
  }

  const [ start, elbow, end ] = points;
  const sourceVertical = start.x === elbow.x && start.y !== elbow.y;
  const sourceHorizontal = start.y === elbow.y && start.x !== elbow.x;
  const targetVertical = end.x === elbow.x && end.y !== elbow.y;
  const targetHorizontal = end.y === elbow.y && end.x !== elbow.x;

  if (!(
    (sourceVertical && targetHorizontal) ||
    (sourceHorizontal && targetVertical)
  )) {
    return points;
  }

  const sourceDock = sourceVertical
    ? point(
      sourceBounds.x + sourceBounds.width / 2,
      elbow.y < start.y ? sourceBounds.y : sourceBounds.y + sourceBounds.height
    )
    : point(
      elbow.x < start.x ? sourceBounds.x : sourceBounds.x + sourceBounds.width,
      sourceBounds.y + sourceBounds.height / 2
    );
  const targetDock = targetVertical
    ? point(
      targetBounds.x + targetBounds.width / 2,
      elbow.y < end.y ? targetBounds.y : targetBounds.y + targetBounds.height
    )
    : point(
      elbow.x < end.x ? targetBounds.x : targetBounds.x + targetBounds.width,
      targetBounds.y + targetBounds.height / 2
    );
  const centered = sourceVertical
    ? [ sourceDock, point(sourceDock.x, targetDock.y), targetDock ]
    : [ sourceDock, point(targetDock.x, sourceDock.y), targetDock ];
  const obstacles = connectionObstacles(shapes);

  if (endpointRouteIsClear(
    centered,
    obstacles,
    sourceElement,
    element.targetRef,
    routedConnections
  )) {
    return centered;
  }

  const sourceCenterX = sourceBounds.x + sourceBounds.width / 2;
  const sourceCenterY = sourceBounds.y + sourceBounds.height / 2;
  const targetCenterX = targetBounds.x + targetBounds.width / 2;
  const targetCenterY = targetBounds.y + targetBounds.height / 2;
  const transposedSourceDock = sourceVertical
    ? point(
      targetCenterX < sourceCenterX ? sourceBounds.x : sourceBounds.x + sourceBounds.width,
      sourceCenterY
    )
    : point(
      sourceCenterX,
      targetCenterY < sourceCenterY ? sourceBounds.y : sourceBounds.y + sourceBounds.height
    );
  const transposedTargetDock = sourceVertical
    ? point(
      targetCenterX,
      sourceCenterY < targetCenterY ? targetBounds.y : targetBounds.y + targetBounds.height
    )
    : point(
      sourceCenterX < targetCenterX ? targetBounds.x : targetBounds.x + targetBounds.width,
      targetCenterY
    );
  const transposed = sourceVertical
    ? [
      transposedSourceDock,
      point(transposedTargetDock.x, transposedSourceDock.y),
      transposedTargetDock
    ]
    : [
      transposedSourceDock,
      point(transposedSourceDock.x, transposedTargetDock.y),
      transposedTargetDock
    ];
  const alternateTargetDock = sourceVertical
    ? point(
      targetCenterX,
      sourceCenterY < targetCenterY ? targetBounds.y : targetBounds.y + targetBounds.height
    )
    : point(
      sourceCenterX < targetCenterX ? targetBounds.x : targetBounds.x + targetBounds.width,
      targetCenterY
    );
  const direction = sourceVertical
    ? alternateTargetDock.y === targetBounds.y ? -1 : 1
    : alternateTargetDock.x === targetBounds.x ? -1 : 1;
  const channel = sourceVertical
    ? alternateTargetDock.y + direction * ROUTING_MARGIN
    : alternateTargetDock.x + direction * ROUTING_MARGIN;
  const bypass = sourceVertical
    ? [
      sourceDock,
      point(sourceDock.x, channel),
      point(alternateTargetDock.x, channel),
      alternateTargetDock
    ]
    : [
      sourceDock,
      point(channel, sourceDock.y),
      point(channel, alternateTargetDock.y),
      alternateTargetDock
    ];

  if (endpointRouteIsClear(
    bypass,
    obstacles,
    sourceElement,
    element.targetRef,
    routedConnections
  )) {
    return bypass;
  }

  if (endpointRouteIsClear(
    transposed,
    obstacles,
    sourceElement,
    element.targetRef,
    routedConnections
  )) {
    return transposed;
  }

  const facingSourceDock = facingDock(point(targetCenterX, targetCenterY), sourceBounds);
  const facingTargetDock = facingDock(point(sourceCenterX, sourceCenterY), targetBounds);
  const sourceStub = outwardDockingStub(facingSourceDock, sourceBounds);
  const targetStub = outwardDockingStub(facingTargetDock, targetBounds);

  if (endpointRouteIsClear(
    centered,
    obstacles,
    sourceElement,
    element.targetRef,
    []
  )) {
    return centered;
  }

  if (endpointRouteIsClear(
    bypass,
    obstacles,
    sourceElement,
    element.targetRef,
    []
  )) {
    return bypass;
  }

  if (endpointRouteIsClear(
    transposed,
    obstacles,
    sourceElement,
    element.targetRef,
    []
  )) {
    return transposed;
  }

  const visibilityRouteWithCrossings = visibilityRoute(
    sourceStub,
    targetStub,
    obstacles,
    sourceElement,
    element.targetRef,
    []
  );

  return visibilityRouteWithCrossings
    ? cleanPoints([
      facingSourceDock,
      ...visibilityRouteWithCrossings,
      facingTargetDock
    ])
    : points;
}

function endpointRouteIsClear(
    points,
    obstacles,
    sourceElement,
    targetElement,
    routedConnections) {
  if (!pathIsClear(points, obstacles, sourceElement, targetElement, [])) {
    return false;
  }

  return !toSegments(points).some(([ a, b ]) => {
    return routedConnections.some(connection => {
      return toSegments(connection.points).some(([ c, d ]) => {
        return segmentsProperlyCross(a, b, c, d);
      });
    });
  });
}

function flipTangentElbow(element, sourceElement, points, sourceBounds, targetBounds, shapes) {
  if (!is(sourceElement, 'bpmn:BoundaryEvent') ||
      points.length !== 3 ||
      dockingDirectionMatches(points[0], points[1], sourceBounds) ||
      dockingDirectionMatches(points.at(-1), points.at(-2), targetBounds)) {
    return points;
  }

  const alternate = cleanPoints([
    points[0],
    point(points[0].x, points.at(-1).y),
    points.at(-1)
  ]);

  if (alternate.length !== 3) {
    return points;
  }

  return pathIsClear(
    alternate,
    connectionObstacles(shapes),
    sourceElement,
    element.targetRef,
    []
  ) ? alternate : points;
}

function connectionObstacles(shapes) {
  return [ ...shapes.entries() ]
    .filter(([ candidate ]) => {
      return !is(candidate, 'bpmn:Lane') &&
        !is(candidate, 'bpmn:Participant') &&
        !isArtifact(candidate);
    })
    .map(([ candidate, rect ]) => ({ element: candidate, rect }));
}

function orientPlaneDockings(factory, planeElements) {
  const shapes = new Map(
    planeElements
      .filter(di => di.$instanceOf('bpmndi:BPMNShape'))
      .map(di => [ di.bpmnElement.id, di ])
  );

  for (const di of planeElements.filter(di => di.$instanceOf('bpmndi:BPMNEdge'))) {
    const points = di.waypoint;

    if (points.length < 2) {
      continue;
    }

    const element = di.bpmnElement;
    const source = Array.isArray(element.sourceRef) ? element.sourceRef[0] : element.sourceRef;
    const sourceShape = shapes.get(source?.id);
    const targetShape = shapes.get(element.targetRef?.id);
    const sourceBounds = sourceShape?.bounds;
    const targetBounds = targetShape?.bounds;
    const requireOrthogonal = !is(element, 'bpmn:Association') &&
      !is(element, 'bpmn:DataAssociation');

    if (sourceBounds) {
      orientPlaneEdgeDocking(
        points,
        sourceBounds,
        true,
        true,
        (x, y) => factory.createDiWaypoint({ x, y }),
        requireOrthogonal,
        requiresCenteredDocking(sourceShape?.bpmnElement)
      );
    }
    if (targetBounds) {
      orientPlaneEdgeDocking(
        points,
        targetBounds,
        false,
        true,
        (x, y) => factory.createDiWaypoint({ x, y }),
        requireOrthogonal,
        requiresCenteredDocking(targetShape?.bpmnElement)
      );
    }
  }
}

function orientPlaneEdgeDocking(
    points,
    rect,
    source,
    allowDogleg = false,
    createPoint = point,
    requireOrthogonal = true,
    centerSides = false) {
  while (points.length > 1) {
    const endpointIndex = source ? 0 : points.length - 1;
    const adjacentIndex = source ? 1 : points.length - 2;
    const endpoint = points[endpointIndex];
    const adjacent = points[adjacentIndex];
    let dock = orientDockingPoint(endpoint, adjacent, rect, requireOrthogonal);

    if (centerSides) {
      dock = centerDockingPoint(dock, adjacent, rect);

      if (dock.x !== endpoint.x || dock.y !== endpoint.y) {
        endpoint.x = dock.x;
        endpoint.y = dock.y;

        if (
          allowDogleg &&
          endpoint.x !== adjacent.x &&
          endpoint.y !== adjacent.y
        ) {
          addDockingDogleg(points, endpointIndex, adjacent, rect, source, createPoint);
          return;
        }
      }
    }

    if (
      allowDogleg &&
      requireOrthogonal &&
      moveAmbiguousCornerDocking(endpoint, adjacent, rect)
    ) {
      addDockingDogleg(
        points,
        endpointIndex,
        adjacent,
        rect,
        source,
        createPoint,
        true
      );
      return;
    }

    if (
      allowDogleg &&
      requireOrthogonal &&
      dockingDirectionMatches(endpoint, adjacent, rect, false) &&
      !dockingDirectionMatches(endpoint, adjacent, rect, true)
    ) {
      addDockingDogleg(points, endpointIndex, adjacent, rect, source, createPoint);
      return;
    }

    if (allowDogleg && dock.x === adjacent.x && dock.y === adjacent.y) {
      addDockingDogleg(points, endpointIndex, adjacent, rect, source, createPoint);
      return;
    }

    if (points.length > 2 && pointIsWithin(adjacent, rect)) {
      points.splice(adjacentIndex, 1);
      continue;
    }

    endpoint.x = dock.x;
    endpoint.y = dock.y;

    if (points.length > 2 && endpoint.x === adjacent.x && endpoint.y === adjacent.y) {
      points.splice(adjacentIndex, 1);
      continue;
    }

    return;
  }
}

function requiresCenteredDocking(element) {
  return is(element, 'bpmn:Event') || is(element, 'bpmn:Gateway');
}

function centerDockingPoint(dock, adjacent, rect) {
  if (dock.x === adjacent.x) {
    if (dock.y === rect.y) {
      return point(rect.x + rect.width / 2, rect.y);
    }
    if (dock.y === rect.y + rect.height) {
      return point(rect.x + rect.width / 2, rect.y + rect.height);
    }
  }
  if (dock.y === adjacent.y) {
    if (dock.x === rect.x) {
      return point(rect.x, rect.y + rect.height / 2);
    }
    if (dock.x === rect.x + rect.width) {
      return point(rect.x + rect.width, rect.y + rect.height / 2);
    }
  }

  return dock;
}

function addDockingDogleg(
    points,
    endpointIndex,
    adjacent,
    rect,
    source,
    createPoint,
    replaceAdjacentBridge = false) {
  const endpoint = points[endpointIndex];
  let outward;
  let bridge;

  const onHorizontalSide =
    (endpoint.y === rect.y || endpoint.y === rect.y + rect.height) &&
    endpoint.x > rect.x &&
    endpoint.x < rect.x + rect.width;

  if (onHorizontalSide || (
    adjacent.y === endpoint.y &&
    (endpoint.y === rect.y || endpoint.y === rect.y + rect.height)
  )) {
    const direction = endpoint.y === rect.y ? -1 : 1;
    const y = endpoint.y + direction * ROUTING_MARGIN;

    outward = createPoint(endpoint.x, y);
    bridge = createPoint(adjacent.x, y);
  } else {
    const direction = endpoint.x === rect.x ? -1 : 1;
    const x = endpoint.x + direction * ROUTING_MARGIN;

    outward = createPoint(x, endpoint.y);
    bridge = createPoint(x, adjacent.y);
  }

  const continuationIndex = source ? 2 : endpointIndex - 2;
  const continuation = points[continuationIndex];
  const bridgeContinuesAdjacentSegment = replaceAdjacentBridge && continuation && (
    (
      bridge.x === adjacent.x &&
      continuation.x === adjacent.x
    ) ||
    (
      bridge.y === adjacent.y &&
      continuation.y === adjacent.y
    )
  );
  let dogleg;

  if (bridgeContinuesAdjacentSegment) {
    adjacent.x = bridge.x;
    adjacent.y = bridge.y;
    dogleg = [ outward ];
  } else {
    dogleg = bridge.x === adjacent.x && bridge.y === adjacent.y
      ? [ outward ]
      : [ outward, bridge ];
  }

  if (source) {
    points.splice(1, 0, ...dogleg);
  } else {
    points.splice(endpointIndex, 0, ...dogleg.reverse());
  }
}

function moveAmbiguousCornerDocking(endpoint, adjacent, rect) {
  const onVerticalSide =
    endpoint.x === rect.x ||
    endpoint.x === rect.x + rect.width;
  const onHorizontalSide =
    endpoint.y === rect.y ||
    endpoint.y === rect.y + rect.height;

  if (!onVerticalSide || !onHorizontalSide) {
    return false;
  }

  if (endpoint.y === adjacent.y) {
    endpoint.x += endpoint.x === rect.x ? ROUTING_MARGIN : -ROUTING_MARGIN;
    return true;
  }

  if (endpoint.x === adjacent.x) {
    endpoint.y += endpoint.y === rect.y ? ROUTING_MARGIN : -ROUTING_MARGIN;
    return true;
  }

  return false;
}

function pointIsWithin(candidatePoint, rect) {
  return candidatePoint.x >= rect.x &&
    candidatePoint.x <= rect.x + rect.width &&
    candidatePoint.y >= rect.y &&
    candidatePoint.y <= rect.y + rect.height;
}

function orientDockingPoint(endpoint, adjacent, rect, requireOrthogonal = true) {
  if (dockingDirectionMatches(endpoint, adjacent, rect, requireOrthogonal)) {
    return endpoint;
  }

  if (endpoint.x === adjacent.x) {
    return point(
      endpoint.x,
      adjacent.y < endpoint.y ? rect.y : rect.y + rect.height
    );
  }
  if (endpoint.y === adjacent.y) {
    return point(
      adjacent.x < endpoint.x ? rect.x : rect.x + rect.width,
      endpoint.y
    );
  }
  if (adjacent.x < rect.x) {
    return point(rect.x, Math.max(rect.y, Math.min(adjacent.y, rect.y + rect.height)));
  }
  if (adjacent.x > rect.x + rect.width) {
    return point(
      rect.x + rect.width,
      Math.max(rect.y, Math.min(adjacent.y, rect.y + rect.height))
    );
  }
  if (adjacent.y < rect.y) {
    return point(Math.max(rect.x, Math.min(adjacent.x, rect.x + rect.width)), rect.y);
  }
  if (adjacent.y > rect.y + rect.height) {
    return point(
      Math.max(rect.x, Math.min(adjacent.x, rect.x + rect.width)),
      rect.y + rect.height
    );
  }

  return endpoint;
}

function dockingDirectionMatches(endpoint, adjacent, rect, requireOrthogonal = true) {
  const vertical = !requireOrthogonal || endpoint.x === adjacent.x;
  const horizontal = !requireOrthogonal || endpoint.y === adjacent.y;

  return (vertical && (
    (endpoint.y === rect.y && adjacent.y < endpoint.y) ||
    (endpoint.y === rect.y + rect.height && adjacent.y > endpoint.y)
  )) || (horizontal && (
    (endpoint.x === rect.x && adjacent.x < endpoint.x) ||
    (endpoint.x === rect.x + rect.width && adjacent.x > endpoint.x)
  ));
}

/**
 * Orchestrates greenfield BPMN layout across semantic analysis, placement,
 * routing, and DI emission.
 */
class Layouter {
  constructor() {
    this.moddle = new BpmnModdle();
    this.diFactory = new DiFactory(this.moddle);
    this.expandedIds = new Set();
    this.warnings = [];
  }

  async layoutProcess(xml) {
    this.warnings = [];
    const parsed = await this.moddle.fromXML(xml);
    const definitions = parsed.rootElement;
    validateParseWarnings(parsed.warnings || [], xml);
    const root = this.selectRoot(definitions);

    if (!root) {
      return {
        xml: (await this.moddle.toXML(definitions, { format: true })).xml,
        warnings: this.warnings
      };
    }

    this.expandedIds = getExpandedIds(definitions, root);
    validateInputVisuals(definitions, root);
    definitions.diagrams = [];

    const layout = is(root, 'bpmn:Collaboration')
      ? this.layoutCollaboration(root)
      : this.layoutScope(root);

    normalizeLayout(layout);
    this.emitDiagram(definitions, root, layout);
    this.emitCollapsedSubProcessDiagrams(definitions, layout);
    this.warnForMissingDi(root, definitions);

    return {
      xml: (await this.moddle.toXML(definitions, { format: true })).xml,
      warnings: this.warnings
    };
  }

  selectRoot(definitions) {
    const roots = definitions.rootElements || [];
    const collaboration = roots.find(element => is(element, 'bpmn:Collaboration'));

    if (collaboration) {
      const participants = collaboration.participants || [];

      if (!participants.some(participant => participant.processRef)) {
        const invalidParticipant = participants.find(participant => participant.$attrs.processRef);

        if (invalidParticipant) {
          throw new LayoutError(
            'INVALID_PARTICIPANT_PROCESS_REFERENCE',
            invalidParticipant.id,
            'A participant processRef must reference a declared process.'
          );
        }

        throw new LayoutError(
          'UNSUPPORTED_COLLABORATION',
          collaboration.id,
          'A collaboration needs at least one participant with a processRef.'
        );
      }

      return collaboration;
    }

    return roots.find(element => is(element, 'bpmn:Process')) || null;
  }

  layoutCollaboration(collaboration) {
    validateMessageFlows(collaboration.messageFlows || []);

    const layout = createLayout(collaboration);
    const messageFlowEndpointDirections = new Map();
    const addMessageFlowDirection = (element, direction) => {
      if (!messageFlowEndpointDirections.has(element)) {
        messageFlowEndpointDirections.set(element, new Set());
      }

      messageFlowEndpointDirections.get(element).add(direction);
    };

    for (const messageFlow of collaboration.messageFlows || []) {
      addMessageFlowDirection(messageFlow.sourceRef, 'outgoing');
      addMessageFlowDirection(messageFlow.targetRef, 'incoming');
    }
    const participantLayouts = new Map();
    const anchorPositionedParticipants = new Set();
    const expandableParticipants = new Set();
    let nextParticipantY = 0;

    for (const participant of collaboration.participants || []) {
      const process = participant.processRef;

      if (!process) {
        const size = getDefaultSize(participant);
        layout.shapes.set(participant, bounds(0, 0, size.width, size.height));
        anchorPositionedParticipants.add(participant);
        expandableParticipants.add(participant);
        continue;
      }

      const processLayout = this.layoutScope(
        process,
        true,
        messageFlowEndpointDirections
      );
      const participantRect = this.getParticipantContainerBounds(process, processLayout);
      const dx = -participantRect.x;
      const dy = -participantRect.y;

      translateLayout(processLayout, dx, dy);

      layout.shapes.set(participant, bounds(
        0,
        0,
        participantRect.width,
        participantRect.height
      ));
      participantLayouts.set(participant, processLayout);

      if (!hasParticipantContent(processLayout)) {
        anchorPositionedParticipants.add(participant);
        expandableParticipants.add(participant);
      }

      processLayout.emitInParent = true;
      layout.children.push(processLayout);
    }

    const localCollaborationShapes = new Map([
      ...layout.shapes,
      ...getExpandedChildShapes(layout)
    ]);
    const messageFlowChannelOffsets = assignMessageFlowChannelOffsets(
      collaboration,
      localCollaborationShapes
    );

    sizeAndPositionParticipantsFromMessageAnchors(
      collaboration,
      layout.shapes,
      localCollaborationShapes,
      messageFlowChannelOffsets,
      anchorPositionedParticipants,
      expandableParticipants
    );
    const participantOrder = orderParticipantsByMessageFlow(
      collaboration,
      layout.shapes,
      localCollaborationShapes
    );

    for (const participant of participantOrder) {
      const participantBounds = layout.shapes.get(participant);
      const processLayout = participantLayouts.get(participant);

      if (processLayout) {
        const extents = getExtents(processLayout);
        const footprintTop = Math.min(0, extents.minY);
        const footprintBottom = Math.max(participantBounds.height, extents.maxY);
        const participantY = nextParticipantY - footprintTop;

        participantBounds.y = participantY;
        translateLayout(processLayout, 0, participantY);
        nextParticipantY += footprintBottom - footprintTop + VERTICAL_GAP;
      } else {
        participantBounds.y = nextParticipantY;
        nextParticipantY += participantBounds.height + VERTICAL_GAP;
      }
    }

    let collaborationShapes = new Map([
      ...layout.shapes,
      ...getExpandedChildShapes(layout)
    ]);
    const participantPositions = alignParticipantsHorizontally(
      collaboration,
      layout.shapes,
      collaborationShapes,
      getExpandedChildEdges(layout),
      messageFlowChannelOffsets,
      anchorPositionedParticipants,
      expandableParticipants
    );

    for (const [ participant, x ] of participantPositions) {
      const participantBounds = layout.shapes.get(participant);
      const processLayout = participantLayouts.get(participant);
      const dx = x - participantBounds.x;

      participantBounds.x = x;

      if (processLayout) {
        translateLayout(processLayout, dx, 0);
      }
    }

    collaborationShapes = new Map([
      ...layout.shapes,
      ...getExpandedChildShapes(layout)
    ]);
    sizeAndPositionParticipantsFromMessageAnchors(
      collaboration,
      layout.shapes,
      collaborationShapes,
      messageFlowChannelOffsets,
      anchorPositionedParticipants,
      expandableParticipants
    );

    for (const [ participant, dx ] of alignParticipantComponentsLeft(
      collaboration,
      layout.shapes
    )) {
      const participantBounds = layout.shapes.get(participant);
      const processLayout = participantLayouts.get(participant);

      participantBounds.x += dx;

      if (processLayout) {
        translateLayout(processLayout, dx, 0);
      }
    }

    compactParticipantRows(
      participantOrder,
      layout.shapes,
      participantLayouts
    );

    collaborationShapes = new Map([
      ...layout.shapes,
      ...getExpandedChildShapes(layout)
    ]);
    const messageObstacles = getMessageObstacles(collaborationShapes);
    let participantBoundsChanged;

    do {
      const routes = routeMessageFlows(
        collaboration,
        layout.shapes,
        collaborationShapes,
        messageObstacles,
        messageFlowChannelOffsets
      );

      for (const [ messageFlow, points ] of routes) {
        layout.edges.set(messageFlow, points);
      }

      participantBoundsChanged = includeResizableParticipantMessageDocks(
        collaboration,
        layout.shapes,
        layout.edges,
        expandableParticipants
      );
    } while (participantBoundsChanged);

    const artifacts = collaboration.artifacts || [];
    const groups = artifacts.filter(element => is(element, 'bpmn:Group'));
    const artifactRecords = artifacts
      .filter(element => isArtifact(element) && !is(element, 'bpmn:Group'))
      .map((element, index) => this.createRecord(element, index));
    const associations = artifacts.filter(element => is(element, 'bpmn:Association'));

    this.placeArtifacts(
      artifactRecords,
      associations,
      layout,
      [],
      new Set(),
      collaboration.participants.length === 1,
      collaboration.participants.length !== 1
    );
    this.warnings.push(...layoutGroups(groups, layout));

    return layout;
  }

  layoutScope(
      scope,
      participantProcess = false,
      messageFlowEndpointDirections = new Map()) {
    const layout = createLayout(scope);
    const flowElements = scope.flowElements || [];
    const artifacts = scope.artifacts || [];
    const groups = artifacts.filter(element => is(element, 'bpmn:Group'));
    const sequenceFlows = flowElements.filter(element => is(element, 'bpmn:SequenceFlow'));
    const dataAssociations = flowElements.flatMap(element => [
      ...(element.dataInputAssociations || []),
      ...(element.dataOutputAssociations || [])
    ]);
    const associations = [ ...flowElements, ...artifacts ]
      .filter(element => is(element, 'bpmn:Association'))
      .concat(dataAssociations);
    const nodeElements = [ ...new Set([
      ...flowElements.filter(element => {
        return !is(element, 'bpmn:SequenceFlow') &&
          !is(element, 'bpmn:Association') &&
          !is(element, 'bpmn:Group') &&
          !is(element, 'bpmn:DataObject');
      }),
      ...artifacts.filter(element => {
        return isArtifact(element) && !is(element, 'bpmn:Group');
      })
    ]) ];

    const records = nodeElements.map((element, index) => this.createRecord(element, index));

    const recordsByElement = new Map(records.map(record => [ record.element, record ]));

    for (const record of records) {
      if (!is(record.element, 'bpmn:SubProcess')) {
        continue;
      }

      record.child = this.layoutScope(
        record.element,
        false,
        messageFlowEndpointDirections
      );

      if (record.expanded) {
        const childExtents = getExtents(record.child);
        record.size = {
          width: Math.max(MIN_SUB_PROCESS_WIDTH, childExtents.width + 2 * SUB_PROCESS_PADDING),
          height: Math.max(MIN_SUB_PROCESS_HEIGHT, childExtents.height + 2 * SUB_PROCESS_PADDING)
        };
      }

      layout.children.push(record.child);
    }

    validateSequenceFlows(sequenceFlows, recordsByElement, scope);
    validateBoundaryEvents(records, recordsByElement, scope);
    validateLinks(records, scope);

    const graphRecords = records.filter(record => !record.isBoundary && !record.isArtifact &&
      !(is(record.element, 'bpmn:SubProcess') && record.element.triggeredByEvent));
    const graphSet = new Set(graphRecords.map(record => record.element));
    const graphEdges = sequenceFlows.filter(flow => graphSet.has(flow.sourceRef) &&
      graphSet.has(flow.targetRef));
    const boundaryEdges = sequenceFlows.filter(flow => is(flow.sourceRef, 'bpmn:BoundaryEvent'));

    const policy = createSemanticPolicy(scope, graphRecords, graphEdges, boundaryEdges, records);
    const ranks = assignRanks(graphRecords, graphEdges, boundaryEdges, policy);
    policy.backEdges = ranks.backEdges;
    compactSemanticBands(graphRecords, graphEdges, boundaryEdges, ranks, policy);
    placeRecords(graphRecords, ranks, policy);
    clearBoundaryHandlerExits(graphRecords, boundaryEdges, recordsByElement, policy);
    packComponents(scope, graphRecords, graphEdges, boundaryEdges);
    applyLaneMembership(scope, graphRecords, graphEdges, policy, layout);
    placeBoundaryEvents(records, recordsByElement);

    for (const record of graphRecords) {
      layout.shapes.set(record.element, record.bounds);
    }

    for (const record of records.filter(record => record.isBoundary)) {
      layout.shapes.set(record.element, record.bounds);
    }

    this.placeExpandedChildren(records, layout);
    this.routeSequenceFlows(sequenceFlows, layout, policy);
    this.placeEventSubProcesses(records, layout);
    if (participantProcess) {
      const interiorArtifacts = records.filter(record => {
        return record.isArtifact && !isExteriorArtifact(record.element);
      });
      const exteriorArtifacts = records.filter(record => {
        return record.isArtifact && isExteriorArtifact(record.element);
      });

      this.placeArtifacts(
        interiorArtifacts,
        associations,
        layout,
        [],
        messageFlowEndpointDirections
      );

      const participantBounds = this.getParticipantContainerBounds(scope, layout);

      this.placeArtifacts(exteriorArtifacts, associations, layout, [ {
        rect: participantBounds,
        containsOwner: true,
        participant: true
      } ], messageFlowEndpointDirections);
    } else {
      this.placeArtifacts(
        records,
        associations,
        layout,
        [],
        messageFlowEndpointDirections
      );
    }
    this.warnings.push(...layoutGroups(groups, layout));

    return layout;
  }

  createRecord(element, index) {
    const size = getDefaultSize(element);

    if (!size || !isSupportedVisualElement(element)) {
      throw new LayoutError(
        'UNSUPPORTED_ELEMENT',
        element.id,
        `Cannot generate DI for visual BPMN element "${element.$type}".`
      );
    }

    return {
      element,
      index,
      size,
      isBoundary: is(element, 'bpmn:BoundaryEvent'),
      isArtifact: isArtifact(element),
      expanded: is(element, 'bpmn:SubProcess') && this.expandedIds.has(element.id),
      child: null
    };
  }

  getParticipantContainerBounds(process, layout) {
    const extents = getParticipantContentExtents(layout);
    const hasLanes = flattenLanes(process.laneSets || []).length > 0;
    const leadingPadding = hasLanes
      ? PARTICIPANT_HEADER_WIDTH
      : PARTICIPANT_HEADER_WIDTH + SUB_PROCESS_PADDING;
    const trailingPadding = hasLanes ? 0 : SUB_PROCESS_PADDING;
    const verticalPadding = hasLanes ? 0 : SUB_PROCESS_PADDING;
    const width = Math.max(
      MIN_PARTICIPANT_WIDTH,
      extents.width + leadingPadding + trailingPadding
    );
    const height = Math.max(
      MIN_PARTICIPANT_HEIGHT,
      extents.height + 2 * verticalPadding
    );

    return bounds(
      extents.minX - leadingPadding,
      extents.minY - verticalPadding,
      width,
      height
    );
  }

  placeExpandedChildren(records) {
    for (const record of records) {
      if (!record.expanded || !record.child || !record.bounds) {
        continue;
      }
      const extents = getExtents(record.child);
      translateLayout(
        record.child,
        record.bounds.x + SUB_PROCESS_PADDING - extents.minX,
        record.bounds.y + SUB_PROCESS_PADDING - extents.minY
      );
      record.child.emitInParent = true;

      if (needsExpandedSubProcessTitleClearance(
        record.element,
        record.bounds,
        record.child
      )) {
        translateLayout(record.child, 0, EXPANDED_SUBPROCESS_LABEL_HEIGHT);
      }
    }
  }

  placeEventSubProcesses(records, layout) {
    const eventSubProcesses = records.filter(record => {
      return is(record.element, 'bpmn:SubProcess') && record.element.triggeredByEvent;
    });

    if (!eventSubProcesses.length) {
      return;
    }

    let nextEventSubProcessY = getExtents(layout).maxY + VERTICAL_GAP;

    for (const record of eventSubProcesses) {
      if (!record.expanded) {
        record.bounds = bounds(0, nextEventSubProcessY, record.size.width, record.size.height);
        layout.shapes.set(record.element, record.bounds);
        nextEventSubProcessY += record.size.height + VERTICAL_GAP;
        continue;
      }

      const extents = getExtents(record.child);
      const width = Math.max(MIN_SUB_PROCESS_WIDTH, extents.width + 2 * SUB_PROCESS_PADDING);
      const height = Math.max(MIN_SUB_PROCESS_HEIGHT, extents.height + 2 * SUB_PROCESS_PADDING);

      record.bounds = bounds(0, nextEventSubProcessY, width, height);
      layout.shapes.set(record.element, record.bounds);
      translateLayout(
        record.child,
        record.bounds.x + SUB_PROCESS_PADDING - extents.minX,
        record.bounds.y + SUB_PROCESS_PADDING - extents.minY
      );
      record.child.emitInParent = true;
      nextEventSubProcessY += height + VERTICAL_GAP;
    }

  }

  placeArtifacts(
      records,
      associations,
      layout,
      additionalBoundaryContainers = [],
      reservedVerticalEndpointDirections = new Map(),
      avoidParticipantInterior = false,
      preferParticipantSides = true) {
    const artifactRecords = records.filter(record => record.isArtifact);
    const graphShapes = new Map([
      ...layout.shapes,
      ...getExpandedChildShapes(layout)
    ]);
    const graphElements = new Set(graphShapes.keys());
    const owners = new Map();
    const graphObstacles = [ ...graphShapes.entries() ].filter(([ element ]) => {
      return !is(element, 'bpmn:Lane') &&
        !is(element, 'bpmn:Participant') &&
        !isArtifact(element);
    }).map(([ element, rect ]) => ({ element, rect }));
    const graphExtents = getShapeExtents(graphObstacles);
    const placementExtents = getShapeExtents([ ...graphShapes.entries() ]
      .filter(([ element ]) => !isArtifact(element))
      .map(([ element, rect ]) => ({ element, rect })));
    const currentArtifacts = new Set(artifactRecords.map(record => record.element));
    const annotatedMessageEndpoints = new Map();

    for (const association of associations) {
      const endpoints = [
        ...(Array.isArray(association.sourceRef)
          ? association.sourceRef
          : [ association.sourceRef ]),
        association.targetRef
      ];
      const annotation = endpoints.find(endpoint => {
        return currentArtifacts.has(endpoint) &&
          is(endpoint, 'bpmn:TextAnnotation');
      });
      const owner = endpoints.find(endpoint => endpoint !== annotation);

      if (annotation && reservedVerticalEndpointDirections.has(owner)) {
        annotatedMessageEndpoints.set(
          owner,
          reservedVerticalEndpointDirections.get(owner)
        );
      }
    }

    const graphRoutes = [
      ...layout.edges.entries(),
      ...getExpandedChildEdges(layout)
    ]
      .filter(([ element ]) => {
        return is(element, 'bpmn:SequenceFlow') || is(element, 'bpmn:MessageFlow');
      })
      .map(([ element, points ]) => ({ element, points }));
    const reservedRoutes = [ ...annotatedMessageEndpoints ].flatMap(([
      element,
      directions
    ]) => {
      const rect = graphShapes.get(element);

      if (!rect) {
        return [];
      }

      const centerX = rect.x + rect.width / 2;

      return [
        directions.has('incoming') && {
          element,
          points: [
            { x: centerX, y: rect.y },
            { x: centerX, y: rect.y - MAX_ARTIFACT_SEARCH_OFFSET }
          ]
        },
        directions.has('outgoing') && {
          element,
          points: [
            { x: centerX, y: rect.y + rect.height },
            {
              x: centerX,
              y: rect.y + rect.height + MAX_ARTIFACT_SEARCH_OFFSET
            }
          ]
        }
      ].filter(Boolean);
    });
    graphRoutes.push(...reservedRoutes);
    graphRoutes.push(...reservedRoutes);
    const placedArtifacts = [ ...graphShapes.entries() ]
      .filter(([ element ]) => {
        return isArtifact(element) && !currentArtifacts.has(element);
      })
      .map(([ element, rect ]) => ({
        element,
        rect,
        annotationClearance: 0
      }));

    for (const association of associations) {
      const endpoints = [
        ...(Array.isArray(association.sourceRef) ? association.sourceRef : [ association.sourceRef ]),
        association.targetRef
      ];
      const artifact = endpoints.find(endpoint => artifactRecords.some(record => record.element === endpoint));
      const owner = is(association, 'bpmn:DataAssociation')
        ? association.$parent
        : endpoints.find(endpoint => endpoint !== artifact && graphElements.has(endpoint));

      if (!artifact || !owner || !graphElements.has(owner)) {
        continue;
      }

      if (!owners.has(artifact)) {
        owners.set(artifact, []);
      }

      owners.get(artifact).push({
        association,
        owner,
        ownerBounds: graphShapes.get(owner)
      });
    }

    for (const references of owners.values()) {
      const counts = new Map();
      const indices = new Map();

      for (const reference of references) {
        counts.set(reference.owner, (counts.get(reference.owner) || 0) + 1);
      }

      for (const reference of references) {
        const index = indices.get(reference.owner) || 0;

        reference.ownerConnectionIndex = index;
        reference.ownerConnectionCount = counts.get(reference.owner);
        indices.set(reference.owner, index + 1);
      }
    }

    artifactRecords.sort((a, b) => {
      const aReferences = owners.get(a.element)?.length || 0;
      const bReferences = owners.get(b.element)?.length || 0;
      const aArea = artifactSizeCandidates(a.element)[0];
      const bArea = artifactSizeCandidates(b.element)[0];

      return bReferences - aReferences ||
        bArea.width * bArea.height - aArea.width * aArea.height ||
        a.index - b.index;
    });

    for (const record of artifactRecords) {
      const references = owners.get(record.element) || [];
      const ownerBounds = references.length
        ? graphShapes.get(references[0].owner)
        : null;
      const owner = references[0]?.owner;
      const enclosingSubProcesses = ownerBounds
        ? [ ...graphShapes.entries() ].filter(([ element, rect ]) => {
          const centerX = ownerBounds.x + ownerBounds.width / 2;
          const centerY = ownerBounds.y + ownerBounds.height / 2;

          return element !== owner &&
            is(element, 'bpmn:SubProcess') &&
            centerX >= rect.x &&
            centerX <= rect.x + rect.width &&
            centerY >= rect.y &&
            centerY <= rect.y + rect.height;
        })
        : [];
      const subProcessContainer = enclosingSubProcesses
        .map(([ , rect ]) => rect)
        .sort((a, b) => a.width * a.height - b.width * b.height)[0];
      const containingContainers = ownerBounds
        ? findContainingArtifactContainers(ownerBounds, graphShapes)
        : [];
      const container = containingContainers[0];
      const boundaryContainers = [
        ...additionalBoundaryContainers,
        ...[ ...graphShapes.entries() ]
          .filter(([ element ]) => {
            return is(element, 'bpmn:Lane') ||
              is(element, 'bpmn:Participant') ||
              is(element, 'bpmn:SubProcess');
          })
          .map(([ element, rect ]) => ({
            rect,
            containsOwner: containingContainers.includes(rect),
            participant: is(element, 'bpmn:Participant')
          }))
      ];
      const processContainer = subProcessContainer || (
        !container && isExteriorArtifact(record.element)
          ? bounds(
            graphExtents.minX - SUB_PROCESS_PADDING,
            graphExtents.minY - SUB_PROCESS_PADDING,
            graphExtents.width + 2 * SUB_PROCESS_PADDING,
            graphExtents.height + 2 * SUB_PROCESS_PADDING
          )
          : null
      );
      const annotationClearance = subProcessContainer &&
        is(record.element, 'bpmn:TextAnnotation')
        ? EXPANDED_SUBPROCESS_ANNOTATION_CLEARANCE
        : 0;
      const obstacles = graphObstacles.filter(({ element }) => {
        return !enclosingSubProcesses.some(([ subProcess ]) => subProcess === element);
      });
      record.associationObstacles = obstacles;
      const placement = findArtifactPlacement(
        record.element,
        ownerBounds,
        references,
        artifactSizeCandidates(record.element),
        obstacles,
        graphRoutes,
        placedArtifacts,
        container,
        processContainer,
        placementExtents,
        annotationClearance,
        boundaryContainers,
        avoidParticipantInterior,
        preferParticipantSides,
        (
          isExteriorArtifact(record.element) &&
          references.some(({ owner }) => {
            return reservedVerticalEndpointDirections.has(owner);
          })
        ) ? VERTICAL_GAP : 0
      );

      record.size = { width: placement.width, height: placement.height };
      layout.shapes.set(record.element, placement);
      placedArtifacts.push({
        element: record.element,
        rect: placement,
        annotationClearance
      });
    }

    for (const record of artifactRecords) {
      const artifactBounds = layout.shapes.get(record.element);
      const references = owners.get(record.element) || [];

      for (const {
        association,
        owner,
        ownerConnectionIndex,
        ownerConnectionCount
      } of references) {
        layout.edges.set(association, routeArtifactAssociation(
          association,
          owner,
          graphShapes.get(owner),
          record.element,
          artifactBounds,
          ownerConnectionIndex,
          ownerConnectionCount,
          record.associationObstacles,
          graphRoutes
        ));
      }
    }

  }

  routeSequenceFlows(sequenceFlows, layout, policy) {
    const routedConnections = [];
    const shapes = [ ...layout.shapes.entries(), ...getExpandedChildShapes(layout) ]
      .filter(([ element ]) => {
        return !is(element, 'bpmn:Lane') &&
          !is(element, 'bpmn:Participant') &&
          !isArtifact(element);
      })
      .map(([ element, rect ]) => ({ element, rect }));

    const ordered = [ ...sequenceFlows ].sort((a, b) => {
      return edgePriority(a, policy) - edgePriority(b, policy) ||
        policy.edgeOrder.get(a) - policy.edgeOrder.get(b);
    });

    for (const flow of ordered) {
      const source = layout.shapes.get(flow.sourceRef);
      const target = layout.shapes.get(flow.targetRef);

      if (!source || !target) {
        continue;
      }

      const points = routeConnection(flow, source, target, shapes, routedConnections, policy);
      layout.edges.set(flow, points);
      routedConnections.push({ flow, points });
    }
  }

  emitDiagram(definitions, root, layout) {
    const plane = this.diFactory.createDiPlane({
      id: `BPMNPlane_${root.id}`,
      bpmnElement: root,
      planeElement: []
    });
    const diagram = this.diFactory.createDiDiagram({
      id: `BPMNDiagram_${root.id}`,
      plane
    });

    definitions.diagrams.push(diagram);
    emitLayout(this.diFactory, layout, plane.planeElement);
    orientPlaneDockings(this.diFactory, plane.planeElement);
    layoutExternalLabels(this.diFactory, plane.planeElement);
  }

  emitCollapsedSubProcessDiagrams(definitions, layout) {
    for (const child of layout.children) {
      if (child.emitInParent) {
        this.emitCollapsedSubProcessDiagrams(definitions, child);
        continue;
      }

      normalizeLayout(child);
      const plane = this.diFactory.createDiPlane({
        id: `BPMNPlane_${child.scope.id}`,
        bpmnElement: child.scope,
        planeElement: []
      });
      const diagram = this.diFactory.createDiDiagram({
        id: `BPMNDiagram_${child.scope.id}`,
        plane
      });

      definitions.diagrams.push(diagram);
      emitLayout(this.diFactory, child, plane.planeElement);
      layoutExternalLabels(this.diFactory, plane.planeElement);
      this.emitCollapsedSubProcessDiagrams(definitions, child);
    }
  }

  warnForMissingDi(root, definitions) {
    const shapeElements = new Set();
    const edgeElements = new Set();

    for (const diagram of definitions.diagrams) {
      for (const di of diagram.plane.planeElement) {
        if (di.$instanceOf('bpmndi:BPMNShape')) {
          shapeElements.add(di.bpmnElement);
        } else if (di.$instanceOf('bpmndi:BPMNEdge')) {
          edgeElements.add(di.bpmnElement);
        }
      }
    }

    for (const element of getExpectedDiElements(root)) {
      const isShape = isSupportedVisualElement(element);
      const emitted = isShape
        ? shapeElements.has(element)
        : edgeElements.has(element);

      if (emitted || this.warnings.some(warning => warning.elementId === element.id)) {
        continue;
      }

      this.warnings.push(new LayoutWarning(
        'DI_NOT_CREATED',
        element.id,
        `No BPMN DI was created for visual BPMN element "${ element.$type }".`
      ));
    }
  }
}

function compactParticipantRows(participants, participantShapes, participantLayouts) {
  let nextY = 0;
  let collapsedRow = [];
  let collapsedRowY = 0;

  for (const participant of participants) {
    const participantBounds = participantShapes.get(participant);
    const processLayout = participantLayouts.get(participant);

    if (processLayout) {
      const extents = getExtents(processLayout);
      const hasProcessGeometry = processLayout.shapes.size > 0;
      const footprintTop = hasProcessGeometry
        ? Math.min(0, extents.minY - participantBounds.y)
        : 0;
      const footprintBottom = hasProcessGeometry
        ? Math.max(participantBounds.height, extents.maxY - participantBounds.y)
        : participantBounds.height;
      const participantY = nextY - footprintTop;
      const dy = participantY - participantBounds.y;

      participantBounds.y = participantY;
      translateLayout(processLayout, 0, dy);
      nextY += footprintBottom - footprintTop + VERTICAL_GAP;
      collapsedRow = [];
      continue;
    }

    const fitsCurrentRow = collapsedRow.length && collapsedRow.every(rect => {
      return rect.x + rect.width + HORIZONTAL_GAP <= participantBounds.x ||
        participantBounds.x + participantBounds.width + HORIZONTAL_GAP <= rect.x;
    });

    if (fitsCurrentRow) {
      participantBounds.y = collapsedRowY;
      collapsedRow.push(participantBounds);
      continue;
    }

    participantBounds.y = nextY;
    collapsedRowY = nextY;
    collapsedRow = [ participantBounds ];
    nextY += participantBounds.height + VERTICAL_GAP;
  }
}

function getExpectedDiElements(root) {
  const elements = new Set();
  const scopes = new Set();

  const addIfExpected = element => {
    if (isSupportedVisualElement(element) || isSupportedVisualConnection(element)) {
      elements.add(element);
    }
  };

  const collectScope = scope => {
    if (scopes.has(scope)) {
      return;
    }

    scopes.add(scope);
    flattenLanes(scope.laneSets || []).forEach(addIfExpected);

    for (const element of scope.flowElements || []) {
      addIfExpected(element);

      for (const association of [
        ...(element.dataInputAssociations || []),
        ...(element.dataOutputAssociations || [])
      ]) {
        addIfExpected(association);
      }

      if (is(element, 'bpmn:SubProcess')) {
        collectScope(element);
      }
    }

    (scope.artifacts || []).forEach(addIfExpected);
  };

  if (is(root, 'bpmn:Collaboration')) {
    (root.participants || []).forEach(addIfExpected);
    (root.messageFlows || []).forEach(addIfExpected);
    (root.artifacts || []).forEach(addIfExpected);

    for (const participant of root.participants || []) {
      if (participant.processRef) {
        collectScope(participant.processRef);
      }
    }
  } else {
    collectScope(root);
  }

  return elements;
}

function layoutProcess(xml) {
  return new Layouter().layoutProcess(xml);
}

export { LayoutError, LayoutWarning, layoutProcess };
//# sourceMappingURL=index.js.map
