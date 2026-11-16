package de.nemeses.spring.bpmn.rest;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration properties of the BPMN REST API ({@code spring.bpmn.rest.*}).
 * <p>
 * The module brings no security of its own: the application secures {@link #basePath()} itself, e.g. with Spring
 * Security.
 *
 * @param basePath path prefix of all endpoints ({@code spring.bpmn.rest.base-path}); must start with {@code /}.
 * @param deploymentEnabled whether BPMN documents may be deployed at runtime with {@code POST /process-definitions}
 *            ({@code spring.bpmn.rest.deployment-enabled}); if {@code false}, that endpoint answers
 *            {@code 403 Forbidden}.
 * @param defaultPageSize number of items of a page if the request has no {@code size} parameter
 *            ({@code spring.bpmn.rest.default-page-size}); positive.
 * @param maxPageSize largest accepted {@code size} parameter; larger sizes are reduced to it
 *            ({@code spring.bpmn.rest.max-page-size}); not less than {@code defaultPageSize}.
 */
@ConfigurationProperties(prefix = "spring.bpmn.rest")
public record BpmnRestProperties(
        @DefaultValue("/api/bpmn/v1") String basePath,
        @DefaultValue("false") boolean deploymentEnabled,
        @DefaultValue("20") int defaultPageSize,
        @DefaultValue("100") int maxPageSize) {

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
}
