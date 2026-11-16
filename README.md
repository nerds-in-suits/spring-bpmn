# spring-bpmn

Simple BPMN 2.0 engine for Spring Boot 4.1 and Java 25.
It reads BPMN files, registers them as process definitions and executes process instances on a persistent task scheduler.
Licensed under the Apache License 2.0 (see [`LICENSE`](LICENSE)).

## Modules

| Module | Purpose |
|--------|---------|
| [`conventions`](conventions/README.md) | Checkstyle, PMD and SpotBugs rulesets and Eclipse settings for all modules. |
| [`spring-bpmn`](spring-bpmn/README.md) | The BPMN engine and its repository API. |
| [`spring-bpmn-repository-jpa`](spring-bpmn-repository-jpa/README.md) | JPA implementation of the repository API. |
| [`spring-bpmn-rest`](spring-bpmn-rest/README.md) | REST API for process definitions, process instances and user tasks. |
| [`spring-bpmn-ui`](spring-bpmn-ui/README.md) | Single page web UI for process definitions, process instances and their steps. |
| [`spring-tasks`](spring-tasks/README.md) | API and core to schedule and execute persistent tasks. |
| [`spring-tasks-jms`](spring-tasks-jms/README.md) | Task persistence on a JMS queue. |
| [`spring-tasks-jpa`](spring-tasks-jpa/README.md) | Task persistence in a JPA table. |

An application adds `spring-bpmn`, one repository implementation and one task persistence to its classpath; auto-configuration wires them.

## Documentation

- [`ARCHITECTURE.md`](ARCHITECTURE.md): modules, dependency directions, key types, flows and configuration properties.
- [`docs/bpmn-support.md`](docs/bpmn-support.md): which BPMN elements are executed and their limits.
- [`AGENTS.md`](AGENTS.md): build, style, documentation and GitHub workflow rules for contributors.

## Build

```text
mvn clean install
```

Checkstyle, PMD and SpotBugs run on every `mvn verify` and `mvn install`.
