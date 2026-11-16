# spring-bpmn-repository-jpa

JPA implementation of the repository API of [`spring-bpmn`](../spring-bpmn/README.md) (`de.nemeses.spring:spring-bpmn-repository-jpa`).

- Stores process definitions, instances, steps and variables in tables prefixed `spring_bpmn_`.
- Auto-configured when there is an `EntityManagerFactory`.

See [`../ARCHITECTURE.md`](../ARCHITECTURE.md) for the entities and their tables.
