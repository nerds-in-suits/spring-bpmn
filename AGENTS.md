# AGENTS.md

This file provides guidance to Claude Code, Cursor and other agents working in this repository.

## What this repo is

Apache-2.0 Maven multi-module project (`de.nemeses.spring:parent:1.0.0`) for a **Spring Boot 4.1.2 / Java 25** BPMN engine. The README title is the goal; the engine is still minimal (see "Current gaps").

| Module | Artifact | Role |
|--------|----------|------|
| `conventions` | `de.nemeses:conventions:1.2.0` | Checkstyle/PMD/SpotBugs rulesets (`src/main/resources/rulesets/`) + Eclipse prefs (`configuration/eclipse/`). No Java sources. Independent POM (not using the parent POM as parent). |
| `spring-tasks` | `de.nemeses.spring:spring-tasks` | Task scheduling/execution API and core (`TaskService` with worker pool, `TaskPersistence`). |
| `spring-tasks-jpa` | `de.nemeses.spring:spring-tasks-jpa` | JPA `TaskPersistence` (moved out of `spring-tasks`, same package names). |
| `spring-tasks-jms` | `de.nemeses.spring:spring-tasks-jms` | JMS persistence, `JmsTaskPersistence` (queue-backed, embedded Artemis in tests). |
| `spring-bpmn-repository-jpa` | `de.nemeses.spring:spring-bpmn-repository-jpa` | JPA implementation of the `spring-bpmn` repository API (`JpaProcessDefinitionService`, `JpaProcessInstanceRepository`, `JpaProcessStepRepository`; tables `spring_bpmn_process_definition`, `spring_bpmn_process_instance`, `spring_bpmn_process_variable` with one JSON value row per variable, `spring_bpmn_process_step`). Depends on `spring-bpmn`. |
| `spring-bpmn` | `de.nemeses.spring:spring-bpmn` | Intended engine module. Depends on `spring-tasks`. Contains the repository API (`de.nemeses.spring.bpmn.repository`: `ProcessDefinitionService`, `ProcessDefinition`, `ProcessInstanceRepository`, `ProcessInstance`, `ProcessStepRepository`, `ProcessStep`) and `ProcessEngineService` (starts process instances, creates a `ProcessStep` per start event and schedules it as `bpmnNodeTask` with the step id as payload) and `BpmnNodeExecutor` (executes one step per `bpmnNodeTask` under a pessimistic lock on the instance, completes it, creates and schedules steps for the targets of the flows taken; service tasks call `BpmnNodeImplementation` beans; `FlowSelector` picks the outgoing flows, `ConditionEvaluator` evaluates their `conditionExpression`s as SpEL). **Hand-written: `BpmnParser`, startup registration of process definitions (`ProcessDefinitionRegistrar`, `ProcessDefinitionSource`, property `spring.bpmn.process-definition-locations`); minimal engine. BPMN 2.0 XSDs in `src/main/resources/xsd/bpmn` are compiled by XJC (jaxb2-maven-plugin, bindings `src/main/xjb/bpmn.xjb`) into one package per namespace: `de.nemeses.spring.bpmn.model.{bpmn20,bpmndi,dc,di}` (`bpmn20` holds BPMN20.xsd + Semantic.xsd, which share a namespace). The `t` type-name prefix is dropped (`TProcess` -> `Process`; `tError` -> `ErrorType` to avoid `java.lang.Error`).** |

**Read `ARCHITECTURE.md` first** for module responsibilities, dependency directions and planned vs. implemented state. Keep it updated when modules or key types change.

Package root: `de.nemeses.spring.tasks`. License: Apache 2.0 (`LICENSE`). No Maven wrapper. CI lives in `.github/workflows/` (see below).

## GitHub workflow

Do **not** commit or push to `main`. Every task gets its own branch and pull request:

1. `git fetch origin` and create the branch from the latest `origin/main`.
2. Name it `feature/<short-slug>` for features or `bugfix/<short-slug>` for fixes.
3. Keep one task on one branch. Do not mix unrelated changes.
4. Commit on that branch, `git push -u origin HEAD`, then open a PR with `gh pr create`.
5. Do not merge to `main` unless the user explicitly asks. The user normally reviews and merges PRs in the GitHub web UI, so stop after opening the PR.

Untracked local artifacts (`.claude/worktrees/`) are not part of the project; do not stage or commit them. Eclipse `bin/` output is git-ignored.

Examples: `bugfix/jpa-task-persistence`, `feature/agent-github-workflow`.

## Commands

From repo root. No `mvnw`.

```text
mvn clean compile
mvn -pl spring-tasks test
mvn -pl spring-tasks test -Dtest=JsonPayloadTaskImplementationTest
mvn -pl spring-tasks test -Dtest=TaskServiceTest#testSchedule
mvn clean verify
mvn clean install
```

Do **not** pass `-Dtest=...` on the full reactor (`-am` / root `test`): `conventions` has no tests and Maven will fail or skip oddly.

Quality plugins (Checkstyle 14.3.0, PMD 7.28.0, SpotBugs 4.10.5) are declared in the root POM `<build>` with a `check` execution, so they run on **every** `mvn verify` / `install`, and there are no profiles to enable or skip them; `owasp-dependencycheck` is the only opt-in check. Rulesets come from the `conventions` JAR, so a changed ruleset needs `conventions` installed before the other modules see it. `conventions/RULE-SUGGESTIONS.md` lists candidate rules that are not enabled.

CI (`.github/workflows/`): `build.yml` runs `mvn clean install` on Java 25 for every PR and every push to `main`. `release.yml` is a manual `workflow_dispatch` with a `version` input (`x.y.z`); it sets the release version on all modules except `conventions`, builds, commits, tags, then bumps to the next `-SNAPSHOT` and pushes.

Checkstyle also runs on tests (`includeTestSourceDirectory=true`). Files must use **LF** (Windows `\r\n` is a Checkstyle error). No tabs, no trailing whitespace, no `@author` Javadoc, no `System.out`/`System.exit`. Each Java package needs `package-info.java`.

## Architecture (as implemented)

`TaskService` is the public entry:

1. Caller `trigger(beanName, payload)` or `schedule(beanName, payload, earliestExecution)`.
2. Lookup `TaskImplementation` by **Spring bean name** from `Map<String, TaskImplementation<?>>`.
3. Serialize payload via that implementation.
4. `TaskPersistence.persist(beanName, payload, earliestExecution)`.

Execution: `TaskService` is a `SmartLifecycle`; on context start it starts `spring.tasks.pool-size` (default 10, `0` = no worker) worker threads; settings are bound from the `@ConfigurationProperties` record `TaskProperties` (see the property table in `ARCHITECTURE.md`). Each loops on `TaskPersistence.next()`, looks up the `TaskImplementation` by bean name, calls `executeSerialized(payload)` (deserialize + `execute`) and reports `TaskInfo.complete()` or `TaskInfo.fail(e)` for any exception. On stop the workers are interrupted and awaited.

Key types (`spring-tasks/src/main/java/de/nemeses/spring/tasks/`):

- `TaskConfiguration` — `@SpringBootConfiguration` + `@ComponentScan` + `@EnableConfigurationProperties(TaskProperties)`.
- `TaskProperties` — `spring.tasks.pool-size`, `spring.tasks.error-backoff`, `spring.tasks.shutdown-timeout`.
- `TaskImplementation<T>` / `JsonPayloadTaskImplementation<T>` (Jackson 3, `tools.jackson`).
- `TaskPersistence` + `TaskInfo` (`next()`, `complete()`, `fail()`).
- `TaskException`, `PayloadException`.

JPA (`...tasks.jpa`): `JpaTaskPersistenceAutoConfiguration` (`@ConditionalOnBean(EntityManager)`, `@ConditionalOnMissingBean(TaskPersistence)`) registers `JpaTaskPersistence`, which calls `EntityManager.persist` on a `TaskEntity` inside `@Transactional`. `TaskEntity` maps table `spring_tasks` (`beanname`, `payload`, `execution`). This lives in module `spring-tasks-jpa` (package unchanged).

JMS (`...tasks.jms`): `JmsTaskPersistenceAutoConfiguration` (conditional on `jakarta.jms.Message`, `JmsTemplate` and a `ConnectionFactory` bean) registers `JmsTaskPersistence` (queue `spring.tasks.jms.queue`, default `spring.tasks`). `persist` sends a text message with a `beanName` property and uses the JMS 2.0 delivery delay for `earliestExecution`; `next()` blocks on a client-acknowledge session; `complete()` acknowledges, `fail()` recovers the session so the message is redelivered. Tested against embedded Artemis (module `spring-tasks-jms`).

Each implementation module registers its auto-configuration in its own `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` (`spring-tasks` itself has none); the JPA one runs after `HibernateJpaAutoConfiguration` so its `EntityManager` condition can match. New auto-configurations must be added to the imports file of their module.

Tests: JUnit 5 via `spring-boot-starter-test` (Vintage excluded). `JsonPayloadTaskImplementationTest` is a plain unit test. `TaskServiceTest` uses `@SpringBootTest` with `TaskConfiguration` plus a nested config providing a queue-backed `RecordingTaskPersistence` (records `complete()`/`fail()`) and a failing task; bean names `"testTaskImplementation"` / `"failingTaskImplementation"`. Test logging: `spring-tasks/src/test/resources/logback-spring.xml`.

No runnable `@SpringBootApplication` main class.

## Current gaps (do not paper over)

- **`spring-bpmn` executes only a few node types.** `BpmnNodeExecutor` supports `startEvent`, `endEvent` (without event definition), `task`, `serviceTask` (bound via the extension attribute `spring:bean`, namespace `https://nemeses.de/spring-bpmn`, to a `BpmnNodeImplementation` bean), `exclusiveGateway` (first flow in document order without condition or with a true condition, else the default flow) and `parallelGateway` (fork, and join over the pending steps at the gateway). Sequence flow conditions are Spring SpEL (`ConditionEvaluator`, language `https://nemeses.de/spring-bpmn/spel` or the BPMN default XPath URI, which is treated as SpEL); flows out of activities follow BPMN (unconditional + true conditions, default only if none is true); conditional flows out of start events and parallel gateways fail. Service task implementations change variables through `BpmnNodeContext.setVariable`/`removeVariable`; the changes are stored after the node, in its transaction (after the instance is locked, before the outgoing flows are selected and the follow-up steps and tasks are created), so the conditions on the task's outgoing flows see the changed variables and parallel paths of one instance change variables one after the other. An `endEvent` ends the instance once no step of it is pending. Steps have no state: pending means `endDate` is null, and a step parked at a join is a pending step at the join node. A join that can never complete keeps the instance running forever (not detected yet). `task` and `serviceTask` support `standardLoopCharacteristics` (`ActivityLoop`: all iterations in one step and transaction, `loopCondition` as SpEL with variable `loopCounter`, `testBefore`, `loopMaximum`; `BpmnNodeContext.loopCounter()`); a loop whose condition never becomes false and has no `loopMaximum` runs forever in its worker. Other node types and multi-instance activities (`multiInstanceLoopCharacteristics`, design open in #59) fail. Do not invent Camunda-like APIs.
- **Repository modules are minimal.** The repository API (in `spring-bpmn`) only has `ProcessDefinitionService`, `ProcessInstanceRepository` (`create`, `retrieve`, `lock`, `updateEndDate`, `updateVariables`) and `ProcessStepRepository` (steps are never deleted). Task execution runs in `TaskService` workers via `TaskPersistence.next()`; only the JMS backend is tested against a real broker.
- **JPA auto-config is untested.** `TaskServiceTest` uses a recording stub `TaskPersistence`; nothing exercises `JpaTaskPersistence` against a real `EntityManager`.

## Style and extension points

- Match existing Java: constructor injection, package-private constructors for Spring beans, `final` parameters and locals, `this.` for member access, SLF4J via `LoggerFactory`: **no `Logger` fields or constants** (not even `private static final Logger LOG`); always call `LoggerFactory.getLogger(this.getClass())` at the call site, and an empty `/** * */` Javadoc header on classes.
- Configuration: bind properties with a `@ConfigurationProperties` record per module (`TaskProperties`, `JmsTaskProperties`, `JpaTaskProperties`) and `@DefaultValue` defaults, no hard-coded tuning constants. Every new or changed property must be added to the property table in `ARCHITECTURE.md` (name, module, default, meaning).
- Code style is defined by the rulesets and Eclipse settings in `conventions/`.
- New task types: Spring beans implementing `TaskImplementation`; callers pass the **bean name** string.
- New persistence: implement `TaskPersistence`, expose a `@Bean` (or finish auto-config **and** register it in `AutoConfiguration.imports`). Prefer `@ConditionalOnMissingBean(TaskPersistence.class)` so one backend wins.
- Engine work belongs in `spring-bpmn`, built on `TaskService` / named task beans, not a second scheduler.
