# Architecture

Software architecture of `spring-bpmn`, a BPMN 2.0 process engine for Spring Boot. Read this in every session before changing module structure. Update it whenever modules, dependency directions or key types change. Build/style rules are in `AGENTS.md`.

## Goal

`spring-bpmn` reads and interprets BPMN 2.0 files and executes the processes. It delegates scheduling/execution of work to `spring-tasks` and storage of models and history to `spring-bpmn-repository`.

## Modules and dependency directions

```text
spring-bpmn ──> spring-tasks              <── spring-tasks-jpa
     │                                    <── spring-tasks-jms
     └───────> spring-bpmn-repository     <── spring-bpmn-repository-jpa
```

An arrow points from the dependent module to the module it depends on. Implementation modules depend on their API module, never the other way round. `spring-bpmn` depends only on the API modules (`spring-tasks`, `spring-bpmn-repository`); concrete backends are chosen by the application by adding the implementation module to its classpath.

| Module | Responsibility | State |
|--------|----------------|-------|
| `conventions` | Checkstyle/PMD/SpotBugs rulesets and Eclipse prefs. Independent POM. | done |
| `spring-tasks` | Interface to schedule and execute tasks: `TaskService`, `TaskImplementation`, `TaskPersistence`, `Data`. | enqueue only; no poller/executor |
| `spring-tasks-jpa` | Stores tasks in JPA (`JpaTaskPersistence`, `TaskEntity`, table `spring_tasks`). Planned: query JPA store for due tasks and execute them. | persist implemented, untested; querying planned |
| `spring-tasks-jms` | Sends scheduled tasks to a JMS queue and listens to it for execution. | empty auto-config stub |
| `spring-bpmn-repository` | API of the persistence layer of the engine: BPMN models (`ProcessModelRepository`) and audit log of executed processes (`ProcessHistoryRepository`). | skeleton interfaces |
| `spring-bpmn-repository-jpa` | Only repository implementation for now; stores everything in JPA. | empty auto-config skeleton |
| `spring-bpmn` | Reads and interprets BPMN files; uses `spring-tasks` to run steps and `spring-bpmn-repository` for models/history. | POM only, no sources |

## Key types

- `de.nemeses.spring.tasks`: `TaskService` (public entry: `trigger`, `schedule`), `TaskImplementation<T>` / `JsonPayloadTaskImplementation<T>` (Jackson), `TaskPersistence` + record `Data`, `TaskException`, `PayloadException`, `TaskConfiguration`.
- `de.nemeses.spring.tasks.jpa` (module `spring-tasks-jpa`): `JpaTaskPersistenceAutoConfiguration`, `JpaTaskPersistence`, `TaskEntity`.
- `de.nemeses.spring.tasks.jms` (module `spring-tasks-jms`): `JmsTaskPersistenceAutoConfiguration` (stub).
- `de.nemeses.spring.bpmn.repository` (module `spring-bpmn-repository`): `ProcessModelRepository`, `ProcessHistoryRepository`.
- `de.nemeses.spring.bpmn.repository.jpa` (module `spring-bpmn-repository-jpa`): `JpaBpmnRepositoryAutoConfiguration` (skeleton).

## Flow of a task

1. Caller invokes `TaskService.trigger(beanName, payload)` or `schedule(beanName, payload, earliestExecution)`.
2. `TaskService` looks up the `TaskImplementation` by Spring bean name and serializes the payload.
3. The active `TaskPersistence` (JPA or JMS) stores or sends it.
4. Planned: the backend hands due tasks back for execution (JPA: poll for due rows; JMS: queue listener) and calls `TaskImplementation.execute`.

## Extension points

- New task type: Spring bean implementing `TaskImplementation`; referenced by bean name.
- New task backend: new module `spring-tasks-<name>` depending on `spring-tasks`, providing a `TaskPersistence` bean via auto-configuration (`@ConditionalOnMissingBean(TaskPersistence.class)`), registered in the module's own `AutoConfiguration.imports`.
- New repository backend: new module `spring-bpmn-repository-<name>` implementing the repository interfaces the same way.
- Engine logic belongs in `spring-bpmn`, built on `TaskService`; do not add a second scheduler.

## Packaging conventions

Implementation modules keep the package names of their API (`...tasks.jpa`, `...tasks.jms`) so consumers are not affected by the module split. Every package needs `package-info.java`; auto-configurations are registered in the `AutoConfiguration.imports` file of the module that contains them.
