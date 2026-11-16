# spring-bpmn-demo

Runnable demo application (`de.nemeses.spring:spring-bpmn-demo`): a local BPMN engine with the web UI.

- Pulls together [`spring-bpmn`](../spring-bpmn/README.md), [`spring-bpmn-repository-jpa`](../spring-bpmn-repository-jpa/README.md), [`spring-tasks`](../spring-tasks/README.md), [`spring-tasks-jpa`](../spring-tasks-jpa/README.md), [`spring-bpmn-rest`](../spring-bpmn-rest/README.md) and [`spring-bpmn-ui`](../spring-bpmn-ui/README.md); no JMS.
- Stores process definitions, instances, steps and tasks in an H2 file database at `target/demo-db/` of the working directory; Hibernate creates the tables (`ddl-auto: update`).
- The only module packaged as an executable Spring Boot jar (`target/spring-bpmn-demo.jar`); the main class is `DemoApplication`.
- Brings no security: run it locally only.

## Start

```text
spring-bpmn-demo/start.sh            (Linux, macOS, Git Bash)
spring-bpmn-demo\start.cmd           (Windows)
```

The scripts build the jar with Maven (`mvn -pl conventions,spring-bpmn-demo -am install -DskipTests`) if it is missing, then run it from the module directory, so the database ends up in `spring-bpmn-demo/target/demo-db/` and `mvn clean` resets it.
`--build` as first argument rebuilds the jar; all other arguments go to the application, e.g. `start.sh --server.port=9090`.
Without the scripts: `mvn install` and `java -jar spring-bpmn-demo/target/spring-bpmn-demo.jar`.

## URLs

- UI: <http://localhost:8080/bpmn-ui/>
- REST API: <http://localhost:8080/api/bpmn/v1/process-definitions> (see [`spring-bpmn-rest`](../spring-bpmn-rest/README.md)).
- The application logs both URLs with the actual port once the web server listens.

## Sample processes

Registered on startup from `src/main/resources/process-definitions/`:

- `helloWorld`: the service task `greet` (`Greet`) sets `greeting` to `Hello, <name>!`; runs to its end.
- `orderApproval`: the service task `checkOrder` (`CheckOrder`) sets `approvalRequired` when `amount` exceeds 100; then the user task `Approve order` waits, otherwise a script task sets `approved = true`.

Start one with the REST API, e.g.:

```text
curl -H "Content-Type: application/json" -d "{\"processDefinition\":\"orderApproval\",\"variables\":{\"amount\":500}}" http://localhost:8080/api/bpmn/v1/process-instances
```

Further BPMN files can be deployed at runtime, as `spring.bpmn.rest.deployment-enabled` is `true`:

```text
curl -H "Content-Type: application/xml" --data-binary @my-process.bpmn http://localhost:8080/api/bpmn/v1/process-definitions
```

The settings are in `src/main/resources/application.yaml`; every one can be overridden on the command line (`--name=value`).
