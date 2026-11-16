# Architecture

Software architecture of `spring-bpmn`, a BPMN 2.0 process engine for Spring Boot.
Read this before changing module structure or key types.
Build, style and documentation rules are in `AGENTS.md`.
Which BPMN elements are executed, and their limits, is in [`docs/bpmn-support.md`](docs/bpmn-support.md).
The behaviour of each type is described in its Javadoc; this file only says what a type is for.

## Goal

`spring-bpmn` reads and interprets BPMN 2.0 files and executes the processes.
It delegates scheduling and execution of work to `spring-tasks`.
It stores models and history through its repository API (`de.nemeses.spring.bpmn.repository`).

## Modules and dependency directions

```text
spring-bpmn ──> spring-tasks   <── spring-tasks-jpa
     ^                         <── spring-tasks-jms
     ├─────── spring-bpmn-repository-jpa
     └─────── spring-bpmn-rest <── spring-bpmn-ui
```

An arrow points from the dependent module to the module it depends on.
Implementation modules depend on their API module, never the other way round.
`spring-bpmn` depends only on the API module `spring-tasks` and contains the repository API itself.
The application chooses the concrete backends by adding implementation modules to its classpath.

| Module | Responsibility |
|--------|----------------|
| `conventions` | Checkstyle/PMD/SpotBugs rulesets and Eclipse prefs. Independent POM. |
| `spring-bpmn` | Parses BPMN documents, registers process definitions and executes process instances on `spring-tasks`; contains the repository API. |
| `spring-bpmn-demo` | Runnable demo application (executable jar) with all modules except `spring-tasks-jms`, H2 and sample processes; not a library. |
| `spring-bpmn-repository-jpa` | JPA implementation of the repository API of `spring-bpmn`. |
| `spring-bpmn-rest` | REST API (JSON) for process definitions, process instances with their steps, and user tasks. |
| `spring-bpmn-ui` | Single page web UI (Alpine.js, static resources) on the REST API of `spring-bpmn-rest`. |
| `spring-tasks` | API and core to schedule and execute tasks (`TaskService`, `TaskImplementation`, `TaskPersistence`). |
| `spring-tasks-jms` | `TaskPersistence` on a JMS queue. |
| `spring-tasks-jpa` | `TaskPersistence` in a JPA table. |

## Key types

One bullet per type, sorted alphabetically.
Package-private helpers are listed when they hold engine behaviour.

### `de.nemeses.spring.bpmn` (module `spring-bpmn`)

- `BpmnParser`: unmarshals and schema-validates BPMN documents into the generated JAXB model (`BpmnParseException`).
- `ConfiguredLocationsProcessDefinitionSource`: the default `ProcessDefinitionSource`, reading `spring.bpmn.process-definition-locations`.
- `ProcessDefinitionAutoConfiguration`: component-scans the package and `exec` with its invoker sub-packages, but not the sub-packages of other modules (`repository.jpa`, `rest`); needs a `ProcessDefinitionService`, `ProcessInstanceRepository`, `ProcessStepRepository` and `TaskService` bean.
- `ProcessDefinitionRegistrar`: registers every `process` of every `ProcessDefinitionSource` document on startup, skipping unchanged ones.
- `ProcessDefinitionSource`: contributes BPMN documents to register.

### `de.nemeses.spring.bpmn.demo` (module `spring-bpmn-demo`)

- `CheckOrder`, `Greet`: the `BpmnNodeImplementation` beans `checkOrder` and `greet` of the sample processes.
- `DemoApplication`: the `@SpringBootApplication` main class; imports `TaskConfiguration`.
- `UiUrlLogger`: logs the UI and REST URLs once the web server listens.

### `de.nemeses.spring.bpmn.exec` (module `spring-bpmn`)

- `BpmnNodeContext`: what a `BpmnNodeImplementation` sees of its node; collects variable changes (implemented by `task.NodeContext`).
- `BpmnNodeExecutor`: the `bpmnNodeTask` task bean (payload: the step id); executes one `ProcessStep` per task in one transaction under a lock on the instance with `Invoker.execute` for the node type and triggers the target nodes of the flows taken; `completeUserTask` executes a waiting `userTask` step the same way with the variables passed from outside.
- `BpmnNodeImplementation`: application code bound to a `serviceTask` by bean name.
- `ConditionEvaluator`: evaluates `conditionExpression`s and loop conditions as SpEL.
- `FlowSelector`: selects the outgoing sequence flows of a node.
- `Invoker`: triggers (`trigger()`, when a node is reached: creates its steps and schedules their tasks) and executes (`execute()`, runs a pending step) the flow nodes of one type (`nodeType()`, exact class); at most one bean per type. The implementations live in the sub-packages below.
- `InvokerEnhancer`: wraps invokers in another `Invoker` of the same node type where needed (e.g. a loop around an activity); all enhancer beans are injected into `InvokerRegistry`, which passes every invoker through each of them.
- `InvokerRegistry`: collects the `Invoker` beans by node type, enhanced by all `InvokerEnhancer` beans, and triggers nodes through them; used by `BpmnNodeExecutor`, `ProcessEngineService` and `NodeTrigger`.
- `NodeExecution`: the step an `Invoker` executes (instance, step, flows, expression language, flow scope, loop instance, completion variables of a user task); completes steps.
- `NodeTrigger`: how an `Invoker` reaches its node (instance, incoming flow, scope, loop data); creates and schedules steps and triggers other nodes.
- `ProcessDefinitionModelCache`: keeps the parsed BPMN document of each process definition, keyed by name and creation date, held softly; used by `ProcessEngineService` and `BpmnNodeExecutor` instead of parsing `bpmnXml` again.
- `ProcessEngineException`: checked exception of all engine operations.
- `ProcessEngineService`: public entry to start process instances, to list and complete their open user tasks and to update their variables (merge or replace, without the instance lock).
- `ProcessMdc`: the MDC keys `processInstanceId`, `stepId` and `nodeId`, set while `BpmnNodeExecutor` executes a step and (only `processInstanceId`) while `InvokerRegistry` triggers a node, e.g. the start events of a new instance.
- `ProcessNavigator`: finds flow nodes and sequence flows in a process, including nested sub-processes.
- `SubProcessScopes`: triggers the target nodes of the flows taken in their scope and schedules leaving embedded sub-processes.

### `de.nemeses.spring.bpmn.exec.*` invokers (module `spring-bpmn`)

- `call.CallActivityInvoker`: starts and resumes `callActivity` steps and maps variables to and from the child instance.
- `event.EndEventInvoker`: completes an `endEvent`, ends the instance and resumes a waiting call activity step.
- `event.StartEventInvoker`: takes all outgoing flows of a `startEvent`.
- `gateway.ExclusiveGatewayInvoker`: takes one outgoing flow of an `exclusiveGateway`.
- `gateway.ParallelGatewayInvoker`: forks and joins at a `parallelGateway`.
- `loop.MultiInstanceLoop`, `loop.MultiInstanceLoopEnhancer`: wrap the invoker of every activity type and run `multiInstanceLoopCharacteristics` with one step per instance below the pending activity step.
- `subprocess.SubProcessInvoker`: enters and leaves an embedded `subProcess`.
- `task.ActivityLoop`: runs the `standardLoopCharacteristics` of an activity within one step.
- `task.ManualTaskInvoker`: passes a `manualTask` through like a plain `task`.
- `task.ScriptTaskInvoker`, `task.ServiceTaskInvoker`, `task.TaskInvoker`: execute `scriptTask`, `serviceTask` and plain `task`.
- `task.SpelScriptRunner`: runs the SpEL script of a `scriptTask`.
- `task.TaskRunner`: runs the work of a task (with its loop) and stores the variables it changed.
- `task.UserTaskInvoker`: creates a `userTask` step without task and, when `ProcessEngineService.completeUserTask` executes it, merges the given variables, completes it and selects its flows.

### `de.nemeses.spring.bpmn.model.*` (module `spring-bpmn`, generated)

- Generated by XJC (jaxb2-maven-plugin) from the BPMN 2.0 XSDs in `spring-bpmn/src/main/resources/xsd/bpmn`, bindings `spring-bpmn/src/main/xjb/bpmn.xjb`.
- One package per namespace: `bpmn20` (BPMN20.xsd and Semantic.xsd, which share a namespace), `bpmndi`, `dc`, `di`.
- The `t` type-name prefix is dropped (`TProcess` -> `Process`); `tError` becomes `ErrorType` to avoid `java.lang.Error`.

### `de.nemeses.spring.bpmn.repository` (module `spring-bpmn`)

- `NoSuchProcessDefinitionException`, `NoSuchProcessInstanceException`, `NoSuchProcessStepException`: checked "not found" exceptions of the repositories.
- `ProcessDefinition`: one registered version of a process (name, BPMN XML, MD5 hash).
- `ProcessDefinitionService`: registers and retrieves process definition versions; `retrieveLatestAll` lists the latest version of every name.
- `ProcessInstance`: a running or ended instance with its variables; `parentStep()` is the waiting call activity step, if any.
- `ProcessInstanceCount`: number of running and finished instances of one definition name (`ProcessInstanceRepository.countByDefinition`).
- `ProcessInstancePage`: one page of `ProcessInstanceRepository.query` with the total count.
- `ProcessInstanceRepository`: stores instances; `lock` serializes the steps of one instance; `query` pages through the instances filtered by definition and state, `countByDefinition` counts them per definition name.
- `ProcessInstanceSort`: sort order of `ProcessInstanceRepository.query` (start or end date).
- `ProcessStep`: one execution of a flow node; pending while `endDate` is null; `parentStepId()` is the enclosing sub-process or multi-instance activity step; `loopCounter()`/`loopItem()` hold loop data.
- `ProcessStepRepository`: stores steps, which are never deleted and form the trail of an instance.

### `de.nemeses.spring.bpmn.repository.jpa` (module `spring-bpmn-repository-jpa`)

- `JpaBpmnRepositoryAutoConfiguration`: registers the JPA repositories if there is an `EntityManagerFactory`.
- `JpaProcessDefinitionService`, `JpaProcessInstanceRepository`, `JpaProcessStepRepository`: the JPA implementations of the repository API.
- `ProcessDefinitionEntity`: table `spring_bpmn_process_definition`.
- `ProcessInstanceEntity`: table `spring_bpmn_process_instance`.
- `ProcessStepEntity`: table `spring_bpmn_process_step`.
- `ProcessVariableEntity`: table `spring_bpmn_process_variable`, one JSON value row per instance and variable.

### `de.nemeses.spring.bpmn.rest` (module `spring-bpmn-rest`)

- `*Dto`, `StartProcessRequest`: JSON records of the requests and responses; dates are ISO-8601 instants.
- `BpmnRestAutoConfiguration`: registers the controllers in a servlet web application with a `ProcessEngineService` and prefixes their paths with `spring.bpmn.rest.base-path`; brings no security of its own.
- `BpmnRestExceptionHandler`: maps the exceptions of the controllers to RFC 9457 problem details (404 for unknown entities, 422 for other `ProcessEngineException`s, 400 for invalid requests).
- `BpmnRestProperties`: the `spring.bpmn.rest.*` settings.
- `ProcessDefinitionController`: `/process-definitions`, addressed by name; lists the latest versions with instance counts, reads versions and BPMN XML, deploys BPMN documents if enabled; no deletion.
- `ProcessInstanceController`: `/process-instances`; pages, reads and starts instances and lists their steps, filtered and sorted, with the node type from the model.
- `ProcessNodes`: reads the type (BPMN element name) and name of the flow nodes of a definition from the `ProcessDefinitionModelCache`.
- `ProcessVariableController`: `/process-instances/{id}/variables`; reads, merges, replaces, sets and removes the variables of a running instance through `ProcessEngineService.updateVariables`, without locking the instance.
- `UserTaskController`: `/user-tasks`; lists and completes the open user tasks of an instance through `ProcessEngineService`.

### `de.nemeses.spring.bpmn.ui` (module `spring-bpmn-ui`)

- `BpmnUiAutoConfiguration`: in a servlet web application with the REST API, redirects `/bpmn-ui` to `/bpmn-ui/` and forwards that to the static `index.html` of `META-INF/resources/bpmn-ui/` (Alpine.js from its WebJar, hash routes, no Node build).
- `BpmnUiConfigController`: `GET /bpmn-ui/config.json`; tells the pages the REST base path (servlet context path plus `spring.bpmn.rest.base-path`).

### `de.nemeses.spring.tasks` (module `spring-tasks`)

- `JsonPayloadTaskImplementation`: `TaskImplementation` with a JSON payload (Jackson 3, `tools.jackson`).
- `PayloadException`: the payload cannot be serialized or deserialized.
- `TaskConfiguration`: `@SpringBootConfiguration` that imports `TaskService` explicitly (no component scan) and enables `TaskProperties`.
- `TaskException`: a task failed.
- `TaskImplementation`: a task type, as a Spring bean referenced by its bean name.
- `TaskMdc`: the MDC key `taskId`, set by `TaskService` while a task runs, and scopes that set MDC entries and restore the previous values.
- `TaskPersistence`: stores tasks and hands due ones to the workers (`next()`, `TaskInfo.taskId()`, `TaskInfo.complete()`, `TaskInfo.fail()`).
- `TaskProperties`: the `spring.tasks.*` settings.
- `TaskService`: public entry (`trigger`, `schedule`); a `SmartLifecycle` running the worker pool; looks up the `TaskImplementation` beans by name in the bean factory when needed, so task beans may depend on it.

### `de.nemeses.spring.tasks.jms` (module `spring-tasks-jms`)

- `JmsTaskPersistence`: sends tasks as text messages with the JMS 2.0 delivery delay; acknowledges on complete, redelivers on fail.
- `JmsTaskPersistenceAutoConfiguration`: registers it if JMS is on the classpath and there is a `ConnectionFactory` bean.
- `JmsTaskProperties`: `spring.tasks.jms.*`.

### `de.nemeses.spring.tasks.jpa` (module `spring-tasks-jpa`)

- `JpaTaskPersistence`: stores tasks in table `spring_tasks` and claims due ones in batches.
- `JpaTaskPersistenceAutoConfiguration`: registers it if there is an `EntityManagerFactory` (runs after `HibernateJpaAutoConfiguration`) and adds the package of `TaskEntity` to the entity scan.
- `JpaTaskProperties`: `spring.tasks.jpa.*`.
- `TaskEntity`: table `spring_tasks`.

## Flow of a task

1. A caller invokes `TaskService.trigger(beanName, payload)` or `schedule(beanName, payload, earliestExecution)`.
2. `TaskService` looks up the `TaskImplementation` by Spring bean name and serializes the payload.
3. The active `TaskPersistence` stores or sends it.
4. Each worker thread loops on `TaskPersistence.next()`, executes the task with its `TaskImplementation` and reports `TaskInfo.complete()` or `TaskInfo.fail(e)`.

## Flow of a process instance

1. `ProcessEngineService.startProcess` creates the instance and triggers each start event (`Invoker.trigger`), which creates a `ProcessStep` and a `bpmnNodeTask` with the step id.
2. The `bpmnNodeTask` bean `BpmnNodeExecutor` locks the instance, runs the node (`Invoker.execute`) and completes the step.
3. For each sequence flow taken, the executor triggers the target node (`Invoker.trigger`), which creates its step and, unless the node waits, a `bpmnNodeTask`, all in the same transaction.
4. A failing step rolls back and its task fails; `JmsTaskPersistence` redelivers it, `JpaTaskPersistence` marks it `FAILED` without retry.

## Extension points

- New BPMN element: an `Invoker` bean for its node type in a sub-package of `de.nemeses.spring.bpmn.exec`, plus its line in `docs/bpmn-support.md`.
- New repository backend: new module `spring-bpmn-repository-<name>` implementing the repository interfaces, registered by auto-configuration.
- New task backend: new module `spring-tasks-<name>` providing a `TaskPersistence` bean via auto-configuration (`@ConditionalOnMissingBean(TaskPersistence.class)`).
- New task type: a Spring bean implementing `TaskImplementation`, referenced by bean name.
- Web frontend: the module `spring-bpmn-ui` uses Alpine.js as static resources in its JAR, with Alpine as a WebJar and no Node build (evaluation in #117).
- Engine logic belongs in `spring-bpmn`, built on `TaskService`; do not add a second scheduler.

## Packaging conventions

Implementation modules keep the package names of their API (`...tasks.jpa`, `...tasks.jms`), so consumers are not affected by the module split.
Every package needs `package-info.java`.
`de.nemeses.spring.bpmn.exec` may use `de.nemeses.spring.bpmn`, but never the other way round.
Auto-configurations are registered in `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` of the module that contains them (`spring-tasks` itself has none).

## Configuration properties

Complete list of all configuration properties, sorted alphabetically.
Bind them with one `@ConfigurationProperties` record per module and `@DefaultValue` defaults.

| Property | Module | Default | Description |
|----------|--------|---------|-------------|
| `spring.bpmn.process-definition-locations` | `spring-bpmn` | `classpath*:/process-definitions/*.bpmn` | Resource patterns scanned on startup for BPMN documents to register as process definitions (`ConfiguredLocationsProcessDefinitionSource`). |
| `spring.bpmn.rest.base-path` | `spring-bpmn-rest` | `/api/bpmn/v1` | Path prefix of all REST endpoints; must start with `/` (`BpmnRestProperties`). |
| `spring.bpmn.rest.default-page-size` | `spring-bpmn-rest` | `20` | Page size of paged lists (`GET /process-instances`) without `size` parameter; positive (`BpmnRestProperties`). |
| `spring.bpmn.rest.deployment-enabled` | `spring-bpmn-rest` | `false` | Whether `POST /process-definitions` deploys BPMN documents at runtime; if `false` it answers `403 Forbidden` (`BpmnRestProperties`). |
| `spring.bpmn.rest.max-page-size` | `spring-bpmn-rest` | `100` | Largest page size; larger `size` parameters are reduced to it; not less than the default page size (`BpmnRestProperties`). |
| `spring.tasks.error-backoff` | `spring-tasks` | `1s` | `Duration` a worker pauses after `TaskPersistence.next()` failed, to avoid a busy loop (`TaskProperties`). |
| `spring.tasks.jms.queue` | `spring-tasks-jms` | `spring.tasks` | Name of the JMS queue tasks are sent to and received from (`JmsTaskProperties`). |
| `spring.tasks.jpa.max-results` | `spring-tasks-jpa` | `20` | Maximum number of due tasks claimed (`RUNNING`) per database query and served from memory (`JpaTaskProperties`). |
| `spring.tasks.pool-size` | `spring-tasks` | `10` | Number of worker threads `TaskService` starts; `0` starts none (tasks are only scheduled), negative values are rejected (`TaskProperties`). |
| `spring.tasks.shutdown-timeout` | `spring-tasks` | `30s` | Maximum `Duration` `TaskService` waits for the interrupted workers when the context stops (`TaskProperties`). |
