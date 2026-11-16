# spring-tasks-jpa

`TaskPersistence` of [`spring-tasks`](../spring-tasks/README.md) in a JPA table (`de.nemeses.spring:spring-tasks-jpa`).

- Stores tasks in table `spring_tasks` and claims due ones in batches.
- Auto-configured when there is an `EntityManagerFactory`.
- Configured by the `spring.tasks.jpa.*` properties.

See [`../ARCHITECTURE.md`](../ARCHITECTURE.md) for the key types and the configuration properties.
