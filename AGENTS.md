# AGENTS.md

This file provides guidance to Claude Code, Cursor and other agents working in this repository.

## What this repo is

Apache-2.0 Maven multi-module project (`de.nemeses.spring:parent:1.0.0`) for a **Spring Boot 4.1.2 / Java 25** BPMN engine. The README title is the goal; **there is no BPMN parser, process definition, token, or worker yet**.

| Module | Artifact | Role |
|--------|----------|------|
| `conventions` | `de.nemeses:conventions:1.2.0` | Checkstyle/PMD/SpotBugs rulesets (`src/main/resources/rulesets/`) + Eclipse prefs (`configuration/eclipse/`). No Java sources. Independent POM (not using the parent POM as parent). |
| `spring-tasks` | `de.nemeses.spring:spring-tasks` | Task scheduling API and core (`TaskService`, `TaskPersistence`). |
| `spring-tasks-jpa` | `de.nemeses.spring:spring-tasks-jpa` | JPA `TaskPersistence` (moved out of `spring-tasks`, same package names). |
| `spring-tasks-jms` | `de.nemeses.spring:spring-tasks-jms` | JMS persistence, `JmsTaskPersistence` (queue-backed, embedded Artemis in tests). |
| `spring-bpmn-repository` | `de.nemeses.spring:spring-bpmn-repository` | Repository API for BPMN models and process history. Interfaces only. |
| `spring-bpmn-repository-jpa` | `de.nemeses.spring:spring-bpmn-repository-jpa` | JPA implementation of the repository, auto-config skeleton only. |
| `spring-bpmn` | `de.nemeses.spring:spring-bpmn` | Intended engine module. Depends on `spring-tasks` and `spring-bpmn-repository`. **POM only, no `src/`.** |

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
4. `TaskPersistence.persist(Data(beanName, payload, earliestExecution))`.

There is **no** poller/executor that later deserializes and calls `TaskImplementation.execute`. Persistence is fire-and-forget enqueue.

Key types (`spring-tasks/src/main/java/de/nemeses/spring/tasks/`):

- `TaskConfiguration` — `@SpringBootConfiguration` + `@ComponentScan` (used by tests).
- `TaskImplementation<T>` / `JsonPayloadTaskImplementation<T>` (Jackson 3, `tools.jackson`).
- `TaskPersistence` + record `Data`.
- `TaskException`, `PayloadException`.

JPA (`...tasks.jpa`): `JpaTaskPersistenceAutoConfiguration` (`@ConditionalOnBean(EntityManager)`, `@ConditionalOnMissingBean(TaskPersistence)`) registers `JpaTaskPersistence`, which calls `EntityManager.persist` on a `TaskEntity` inside `@Transactional`. `TaskEntity` maps table `spring_tasks` (`beanname`, `payload`, `execution`). This lives in module `spring-tasks-jpa` (package unchanged).

JMS (`...tasks.jms`): `JmsTaskPersistenceAutoConfiguration` (conditional on `jakarta.jms.Message`, `JmsTemplate` and a `ConnectionFactory` bean) registers `JmsTaskPersistence` (queue `spring.tasks.jms.queue`, default `spring.tasks`). `persist` sends a text message with a `beanName` property and uses the JMS 2.0 delivery delay for `earliestExecution`; `next()` blocks on a client-acknowledge session; `complete()` acknowledges, `fail()` recovers the session so the message is redelivered. Tested against embedded Artemis (module `spring-tasks-jms`).

Each implementation module registers its auto-configuration in its own `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` (`spring-tasks` itself has none); the JPA one runs after `HibernateJpaAutoConfiguration` so its `EntityManager` condition can match. New auto-configurations must be added to the imports file of their module.

Tests: JUnit 5 via `spring-boot-starter-test` (Vintage excluded). `JsonPayloadTaskImplementationTest` is a plain unit test. `TaskServiceTest` uses `@SpringBootTest` with `TaskConfiguration` plus a nested config providing a recording `TaskPersistence`, and bean name `"testTaskImplementation"`. Test logging: `spring-tasks/src/test/resources/logback-spring.xml`.

No runnable `@SpringBootApplication` main class.

## Current gaps (do not paper over)

- **`spring-bpmn` has no sources.** Do not invent Camunda-like APIs or BPMN XML until that module exists.
- **Repository modules are skeletons.** `spring-bpmn-repository` has two minimal interfaces (`ProcessModelRepository`, `ProcessHistoryRepository`); `spring-bpmn-repository-jpa` has an empty auto-configuration and no entities. Task polling/execution (JPA query for due tasks, JMS listener) is not implemented either.
- **JPA auto-config is untested.** `TaskServiceTest` uses a recording stub `TaskPersistence`; nothing exercises `JpaTaskPersistence` against a real `EntityManager`.

## Style and extension points

- Match existing Java: constructor injection, package-private constructors for Spring beans, `final` parameters and locals, `this.` for member access, SLF4J via `LoggerFactory`, and an empty `/** * */` Javadoc header on classes.
- Code style is defined by the rulesets and Eclipse settings in `conventions/`.
- New task types: Spring beans implementing `TaskImplementation`; callers pass the **bean name** string.
- New persistence: implement `TaskPersistence`, expose a `@Bean` (or finish auto-config **and** register it in `AutoConfiguration.imports`). Prefer `@ConditionalOnMissingBean(TaskPersistence.class)` so one backend wins.
- Engine work belongs in `spring-bpmn`, built on `TaskService` / named task beans, not a second scheduler.
