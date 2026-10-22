# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

A simple BPMN engine for Spring (early stage). Multi-module Maven build (Spring Boot 3.5.7 parent, Java 25, group `de.nemeses.spring`):

- `conventions` – packaged ruleset jar (`de.nemeses:conventions`) holding Checkstyle/PMD/SpotBugs configs in `src/main/resources/rulesets/`, plus Eclipse formatter/template settings in `configuration/eclipse/`. The quality plugins in the root POM load their rulesets from this artifact (version `${conventions.version}`), so it must be built/installed first.
- `spring-tasks` – persistent task scheduler library (the only module with code so far).
- `spring-bpmn` – the BPMN engine; currently just a POM depending on `spring-tasks`.

## Commands

```
mvn clean install                          # build everything, run tests
mvn -pl spring-tasks test                  # one module
mvn -pl spring-tasks test -Dtest=TaskServiceTest#testSchedule   # single test
mvn install -Pcodechecks                   # SpotBugs + PMD + Checkstyle (also: -Pfindbugs, -Ppmd, -Pcheckstyle)
mvn verify -Powasp-dependencycheck         # OWASP dependency check
```

The default `nochecks` profile is active by default, so the code-quality plugins only run when you pass one of the profiles above. Code-check plugins resolve `de.nemeses:conventions:1.1.0` while the module in this repo is built as part of the reactor, so install `conventions` before running checks in other modules.

## Architecture (spring-tasks)

Package `de.nemeses.spring.tasks`:

- `TaskService` – public entry point: `trigger(beanName, payload)` / `schedule(beanName, payload, earliestExecution)`. It looks up a `TaskImplementation` by bean name (the Spring bean name, injected as `Map<String, TaskImplementation<?>>`), serializes the payload to a string, and hands a `TaskPersistence.Data` record to the persistence layer. It does not execute tasks yet.
- `TaskImplementation<T>` – user-implemented task: `execute`, plus payload `serializePayload`/`deserializePayload`. `JsonPayloadTaskImplementation` is the Jackson-based base class.
- `TaskPersistence` – storage SPI (`persist(Data)`). The sole implementation is `jpa.JpaTaskPersistence`, which persists a `TaskEntity` via `EntityManager` inside `@Transactional`.
- `jpa.JpaTaskPersistenceAutoConfiguration` – registers `JpaTaskPersistence` when an `EntityManager` bean exists and no other `TaskPersistence` is defined. `jms.JmsTaskPersistenceAutoConfiguration` is an empty placeholder for a JMS-backed persistence.
- `TaskConfiguration` – `@SpringBootConfiguration` + `@ComponentScan`, used as the config class in tests.

JPA and JMS dependencies are `provided` scope: consumers bring their own, which is why persistence is wired through conditional auto-configuration rather than component scanning.

Note: there is currently no `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` in `spring-tasks`, so the auto-configurations are not registered for consuming applications yet. `TaskServiceTest` loads only `TaskConfiguration`, which requires a `TaskPersistence` bean to be available for `TaskService` to be created.

## Conventions

- Code style is defined by the Checkstyle/PMD rulesets and Eclipse settings in `conventions/`. Existing code uses `final` on parameters and locals, `this.` for member access, and an empty `/** * */` Javadoc header on classes.
