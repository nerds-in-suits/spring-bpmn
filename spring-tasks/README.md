# spring-tasks

API and core to schedule and execute persistent tasks (`de.nemeses.spring:spring-tasks`).

- `TaskService` triggers or schedules tasks and runs a pool of worker threads that execute due tasks.
- A task type is a Spring bean implementing `TaskImplementation`, referenced by its bean name.
- `TaskPersistence` stores the tasks; add [`spring-tasks-jpa`](../spring-tasks-jpa/README.md) or [`spring-tasks-jms`](../spring-tasks-jms/README.md) to the classpath for a backend.
- Configured by the `spring.tasks.*` properties.

See [`../ARCHITECTURE.md`](../ARCHITECTURE.md) for the key types, the flow of a task and the configuration properties.
