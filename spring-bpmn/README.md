# spring-bpmn

The BPMN 2.0 engine (`de.nemeses.spring:spring-bpmn`).

- Parses and schema-validates BPMN documents and registers each `process` as a process definition on startup.
- Starts process instances via `ProcessEngineService` and executes each step as a task of `spring-tasks`.
- Binds `serviceTask`s to `BpmnNodeImplementation` beans by bean name and evaluates conditions and scripts as SpEL.
- Contains the repository API (`de.nemeses.spring.bpmn.repository`) for process definitions, instances and steps.

It needs a repository implementation such as [`spring-bpmn-repository-jpa`](../spring-bpmn-repository-jpa/README.md) and a task persistence such as [`spring-tasks-jpa`](../spring-tasks-jpa/README.md) or [`spring-tasks-jms`](../spring-tasks-jms/README.md).
BPMN documents are read from `spring.bpmn.process-definition-locations`.

See [`../docs/bpmn-support.md`](../docs/bpmn-support.md) for the supported BPMN elements and [`../ARCHITECTURE.md`](../ARCHITECTURE.md) for the key types and the flow of a process instance.
