# spring-bpmn-rest

REST API (JSON) of [`spring-bpmn`](../spring-bpmn/README.md) (`de.nemeses.spring:spring-bpmn-rest`).

- Process definitions, addressed by name: list with instance counts, versions, BPMN XML and, if enabled, deployment of BPMN documents.
- Process instances: paged list filtered by definition and state, details with variables, start, and their steps filtered and sorted by state, node type and dates.
- Process variables: read, merge (JSON Merge Patch), replace, and set or remove a single variable of a running instance.
- User tasks: the open user tasks of an instance and their completion with variables.
- Auto-configured in a servlet web application with the engine; endpoints below `spring.bpmn.rest.base-path` (default `/api/bpmn/v1`).
- Errors are RFC 9457 problem details; the module brings no security, so the application secures the base path.
- Configured by the `spring.bpmn.rest.*` properties.

See [`../ARCHITECTURE.md`](../ARCHITECTURE.md) for the key types and the configuration properties, and the Javadoc of the controllers for the endpoints.
