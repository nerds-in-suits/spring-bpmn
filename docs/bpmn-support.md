# BPMN support

Which BPMN 2.0 elements and engine features `spring-bpmn` executes, and their limits.
One bullet per element or feature, sorted alphabetically; one sentence per line.
The details are in the Javadoc of the class named in each bullet.
A step that reaches a flow node not listed as supported fails with `ProcessEngineException`.
Do not invent Camunda-like APIs to fill a gap.

## Elements

- `adHocSubProcess`: not supported (#77).
- `boundaryEvent`: not supported (#61).
- `businessRuleTask`: not supported (#67).
- `callActivity`: supported (`CallActivityInvoker`).
  A wait state: its first execution starts the latest definition named by the local part of `calledElement` as a child instance whose `ProcessInstance.parentStep()` is the call step.
  When the child ends, the call step runs again, passes the variables back, completes and takes its outgoing flows like any activity.
  Without mapping, all variables are copied in and merged back; any `spring:in`/`spring:out` extension element (`source`, `target`) makes both directions explicit.
  A child that fails or never ends leaves the parent waiting, and ending a parent does not end its children (#92).
  Loop characteristics fail (#59).
- `complexGateway`: not supported (#57).
- `endEvent`: supported without event definition (`BpmnNodeExecutor`).
  At the top level it ends the instance once no step of the instance is pending; inside a sub-process it only completes its step.
  If the instance was started by a call activity, its end resumes the waiting call step.
  Event definitions are not supported (#60).
- `eventBasedGateway`: not supported (#57).
- `exclusiveGateway`: supported (`FlowSelector`).
  Takes the first non-default outgoing flow in document order without condition or with a true condition, else the `default` flow, and fails if there is none.
- `inclusiveGateway`: not supported (#57).
- `intermediateCatchEvent`, `intermediateThrowEvent`: not supported (#61).
- `manualTask`: not supported (#65).
- `multiInstanceLoopCharacteristics`: not supported, fails (#59).
- `parallelGateway`: supported (`BpmnNodeExecutor`).
  With at most one incoming flow it is a fork and takes all outgoing flows.
  With more incoming flows it is a join: it continues once a step is pending at the gateway for every incoming flow of the same scope, and parks the step otherwise.
  A join that can never complete keeps the instance running forever (#91).
- `receiveTask`: not supported (#69).
- `scriptTask`: supported (`ScriptTaskInvoker`).
  SpEL only (`scriptFormat` absent or `https://nemeses.de/spring-bpmn/spel`); statements are separated by `;`.
  Writes only by assigning plain variable names (`amount = amount * 2`, `count++`); no `null`, no removal.
- `sendTask`: not supported (#68).
- `sequenceFlow`: supported, with optional `conditionExpression` (see "Expressions").
  An activity takes every flow without condition and every flow whose condition is true, and its `default` flow only if no condition is true.
  Conditional flows out of start events and parallel gateways fail.
- `serviceTask`: supported (`TaskInvoker`).
  Calls the `BpmnNodeImplementation` bean named by the extension attribute `spring:bean` (namespace `https://nemeses.de/spring-bpmn`).
- `standardLoopCharacteristics`: supported on `task`, `serviceTask` and `scriptTask` (`ActivityLoop`).
  All iterations run in one step and transaction; `loopCondition` (SpEL, with the variable `loopCounter`), `testBefore` and `loopMaximum` are evaluated.
  A loop whose condition never becomes false and has no `loopMaximum` runs forever in its worker.
- `startEvent`: supported without event definition (`ProcessEngineService`, `SubProcessScopes`).
  Every start event of the process (or of the entered sub-process) gets a step.
  Event definitions are not supported (#60).
- `subProcess` (embedded): supported (`SubProcessScopes`).
  Its start events get steps whose `ProcessStep.parentStepId()` is the sub-process step, which stays pending until none of its child steps is pending, then takes its outgoing flows like an activity.
  Nested sub-processes and parallel paths inside one work.
  A top-level sub-process without outgoing flow does not end the instance; only a top-level `endEvent` does.
  Event sub-processes (`triggeredByEvent`, #90), a sub-process without start event and loop characteristics (#59) fail.
- `task`: supported, does nothing and takes its outgoing flows like any activity.
- `transaction`: not supported (#77).
- `userTask`: not supported (#64).

## Engine features

- Execution model: one `ProcessStep` per node execution, run by one `bpmnNodeTask` (`BpmnNodeExecutor`).
  A step runs under a pessimistic lock on its instance, so the steps of one instance run one after the other.
  Steps have no state: pending means `endDate` is null, and steps are never deleted.
- Expressions: Spring SpEL (`ConditionEvaluator`), language `https://nemeses.de/spring-bpmn/spel` or the BPMN default XPath URI, which is treated as SpEL.
  Variables are readable by name, as `#name` and via `['name']`; the evaluation context is read-only.
- Process variables: service tasks change them through `BpmnNodeContext.setVariable`/`removeVariable`, script tasks by assignment (`BpmnNodeContext`).
  The changes are stored after the node, in its transaction, so the conditions on its outgoing flows see them and parallel paths of one instance change variables one after the other.
