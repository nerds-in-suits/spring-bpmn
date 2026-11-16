package de.nemeses.spring.bpmn.rest;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration properties of the BPMN REST API ({@code spring.bpmn.rest.*}).
 *
 * @param basePath path prefix of all endpoints ({@code spring.bpmn.rest.base-path}); must start with {@code /}.
 * @param deploymentEnabled whether BPMN documents may be deployed at runtime with {@code POST /process-definitions}
 *            ({@code spring.bpmn.rest.deployment-enabled}); if {@code false}, that endpoint answers
 *            {@code 403 Forbidden}.
 * @param defaultPageSize number of items of a page if the request has no {@code size} parameter
 *            ({@code spring.bpmn.rest.default-page-size}); positive.
 * @param maxPageSize largest accepted {@code size} parameter; larger sizes are reduced to it
 *            ({@code spring.bpmn.rest.max-page-size}); not less than {@code defaultPageSize}.
 * @param security how the endpoints are secured ({@code spring.bpmn.rest.security.*}).
 */
@ConfigurationProperties(prefix = "spring.bpmn.rest")
public record BpmnRestProperties(
        @DefaultValue("/api/bpmn/v1") String basePath,
        @DefaultValue("false") boolean deploymentEnabled,
        @DefaultValue("20") int defaultPageSize,
        @DefaultValue("100") int maxPageSize,
        @DefaultValue Security security) {

    /**
     * Validates the properties.
     */
    public BpmnRestProperties {
        if (basePath == null || !basePath.startsWith("/")) {
            throw new IllegalArgumentException("spring.bpmn.rest.base-path must start with '/': " + basePath);
        }
        if (defaultPageSize < 1) {
            throw new IllegalArgumentException("spring.bpmn.rest.default-page-size must be positive: "
                    + defaultPageSize);
        }
        if (maxPageSize < defaultPageSize) {
            throw new IllegalArgumentException("spring.bpmn.rest.max-page-size must not be less than the default"
                    + " page size: " + maxPageSize);
        }
    }

    /**
     * Security of the REST API ({@code spring.bpmn.rest.security.*}), applied by {@code BpmnRestSecurityConfiguration}
     * with Spring Security.
     *
     * @param enabled whether the module secures the endpoints ({@code spring.bpmn.rest.security.enabled}); if
     *            {@code true} (the default) and Spring Security is not on the classpath, the application fails to
     *            start; only an explicit {@code false} leaves the endpoints unsecured by this module.
     * @param httpBasic whether the endpoints accept HTTP Basic authentication
     *            ({@code spring.bpmn.rest.security.http-basic}).
     * @param authority the authority each operation requires ({@code spring.bpmn.rest.security.authority.*}).
     */
    public record Security(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("true") boolean httpBasic,
            @DefaultValue Authority authority) {
    }

    /**
     * The authority each operation of the REST API requires
     * ({@code spring.bpmn.rest.security.authority.<operation>}); empty or not set means that any authenticated user
     * may call it.
     *
     * @param readDefinitions {@code GET /process-definitions/**} ({@code read-definitions}).
     * @param deploy {@code POST /process-definitions} ({@code deploy}).
     * @param readInstances {@code GET /process-instances/**} and {@code GET /user-tasks/**} ({@code read-instances}).
     * @param startInstance {@code POST /process-instances} ({@code start-instance}).
     * @param updateVariables {@code PATCH}, {@code PUT} and {@code DELETE} of
     *            {@code /process-instances/{id}/variables/**} ({@code update-variables}).
     * @param completeUserTask {@code POST /user-tasks/{id}/complete} ({@code complete-user-task}).
     */
    public record Authority(
            String readDefinitions,
            String deploy,
            String readInstances,
            String startInstance,
            String updateVariables,
            String completeUserTask) {
    }
}
