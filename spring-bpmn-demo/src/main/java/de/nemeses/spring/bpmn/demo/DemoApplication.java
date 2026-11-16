package de.nemeses.spring.bpmn.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

import de.nemeses.spring.tasks.TaskConfiguration;

/**
 * Local BPMN engine with web UI.
 * <p>
 * The auto-configurations of the modules on the classpath wire the engine ({@code spring-bpmn}), its JPA repositories
 * ({@code spring-bpmn-repository-jpa}), the JPA task persistence ({@code spring-tasks-jpa}), the REST API
 * ({@code spring-bpmn-rest}) and the UI ({@code spring-bpmn-ui}); {@code spring-tasks} has no auto-configuration, so
 * its {@link TaskConfiguration} is imported here. The database, port and engine settings are in
 * {@code application.yaml}; the sample processes in {@code process-definitions/} are registered on startup.
 */
@SpringBootApplication
@Import(TaskConfiguration.class)
@SuppressWarnings("checkstyle:HideUtilityClassConstructor") // a configuration class, instantiated by Spring
public class DemoApplication {

    /**
     * Starts the application; the entry point of the executable jar.
     *
     * @param args the command line arguments, e.g. {@code --server.port=9090}
     */
    @SuppressWarnings("checkstyle:UncommentedMain")
    public static void main(final String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
