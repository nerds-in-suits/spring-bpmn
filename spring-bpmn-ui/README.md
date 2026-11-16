# spring-bpmn-ui

Single page web UI of [`spring-bpmn`](../spring-bpmn/README.md) on the REST API of [`spring-bpmn-rest`](../spring-bpmn-rest/README.md) (`de.nemeses.spring:spring-bpmn-ui`).

- Process definitions with the number of their running and finished instances, linking to the filtered instance list.
- Process definitions have a play button that asks for the variables as a JSON object and starts an instance, then shows it.
- Process instances filtered by definition and state, sorted by start or end date, paged.
- Process instance details with variables and its steps, filtered by state and task type, sorted by start date, end date or task type.
- Open it at `/bpmn-ui/` of the application (below the servlet context path); `/bpmn-ui` redirects there.
- Static HTML and JavaScript with [Alpine.js](https://alpinejs.dev/) from its WebJar, no Node build; the pages only call the REST API.
- `GET /bpmn-ui/config.json` tells the pages the REST base path (context path plus `spring.bpmn.rest.base-path`), so a changed base path needs no UI configuration.
- Auto-configured in a servlet web application with the REST API; the module brings no security, so the application secures `/bpmn-ui/` together with the REST base path.

See [`../ARCHITECTURE.md`](../ARCHITECTURE.md) for the key types, and the Javadoc of `BpmnUiAutoConfiguration` for the details.
