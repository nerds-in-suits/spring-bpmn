# spring-bpmn-ui

Single page web UI of [`spring-bpmn`](../spring-bpmn/README.md) on the REST API of [`spring-bpmn-rest`](../spring-bpmn-rest/README.md) (`de.nemeses.spring:spring-bpmn-ui`).

- Process definitions with the number of their running and finished instances, linking to the filtered instance list.
- Process definitions have an Upload button that deploys a chosen BPMN file and names the deployed definitions; it is shown only if `spring.bpmn.rest.deployment-enabled` is `true`.
- Process definitions have a play button that asks for the variables as a JSON object and starts an instance, then shows it.
- Process instances filtered by definition and state, sorted by start or end date with a click on the column header (again reverses the direction), paged.
- Process instance details with the BPMN diagram of its definition version, nodes with running and finished steps highlighted, its variables and its steps, filtered by state and task type, sorted by start date, end date or task type with a click on the column header.
- Without diagram interchange (BPMN DI) in the document, the browser generates a layout with [bpmn-auto-layout](https://github.com/bpmn-io/bpmn-auto-layout) 2.x and says so; the stored definition is not changed.
- If the diagram cannot be laid out or rendered, a message replaces it.
- Open it at `/bpmn-ui/` of the application (below the servlet context path); `/bpmn-ui` redirects there.
- Static HTML and JavaScript with [Alpine.js](https://alpinejs.dev/) from its WebJar, no Node build; the pages only call the REST API.
- Diagrams are rendered by the viewer of [bpmn-js](https://bpmn.io/toolkit/bpmn-js/) (WebJar `org.webjars.npm:bpmn-js`, prebuilt `bpmn-viewer.production.min.js`), whose [bpmn.io license](https://bpmn.io/license/) requires the bpmn.io watermark to stay fully visible and not overlapped; do not hide it.
- `GET /bpmn-ui/config.json` tells the pages the REST base path (context path plus `spring.bpmn.rest.base-path`), so a changed base path needs no UI configuration, and whether deployment is enabled (`deploymentEnabled`).
- Auto-configured in a servlet web application with the REST API; the module brings no security of its own: the application's login (or Spring Boot's default one) secures `/bpmn-ui/`, and the REST API accepts that session.
- The header links to the logout of the application, `spring.bpmn.ui.logout-path` (default `/logout`, Spring Security's confirmation page) below the context path; an empty value hides the link.
- On `401` from the REST API the page reloads once, so the login takes over; on `403` it shows "Not permitted"; modifying requests send the CSRF token of the cookie `XSRF-TOKEN`.

See [`../ARCHITECTURE.md`](../ARCHITECTURE.md) for the key types, and the Javadoc of `BpmnUiAutoConfiguration` for the details.
