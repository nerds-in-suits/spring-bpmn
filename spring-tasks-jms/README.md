# spring-tasks-jms

`TaskPersistence` of [`spring-tasks`](../spring-tasks/README.md) on a JMS queue (`de.nemeses.spring:spring-tasks-jms`).

- Sends tasks as text messages with the JMS 2.0 delivery delay and redelivers failed ones.
- Auto-configured when JMS is on the classpath and there is a `ConnectionFactory` bean.
- Configured by the `spring.tasks.jms.*` properties.

See [`../ARCHITECTURE.md`](../ARCHITECTURE.md) for the key types and the configuration properties.
