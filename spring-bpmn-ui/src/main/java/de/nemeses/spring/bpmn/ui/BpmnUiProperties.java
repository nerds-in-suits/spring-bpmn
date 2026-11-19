package de.nemeses.spring.bpmn.ui;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration properties of the BPMN UI ({@code spring.bpmn.ui.*}).
 *
 * @param logoutPath path of the application's logout below the servlet context path
 *            ({@code spring.bpmn.ui.logout-path}), linked from the header of the pages; empty hides the link, otherwise
 *            it must start with {@code /}. The default {@code /logout} is the logout of Spring Security, whose
 *            {@code GET} shows a confirmation page that logs out with a {@code POST} and its CSRF token.
 * @param refreshInterval interval in which the detail page of a running process instance reloads its data
 *            ({@code spring.bpmn.ui.refresh-interval}); {@code 0} disables the auto-refresh, negative values are
 *            rejected.
 */
@ConfigurationProperties(prefix = "spring.bpmn.ui")
public record BpmnUiProperties(@DefaultValue("/logout") String logoutPath,
        @DefaultValue("10s") Duration refreshInterval) {

    /**
     * Validates the properties; a missing logout path is read as empty, a missing refresh interval as {@code 0}.
     */
    public BpmnUiProperties {
        if (logoutPath == null) {
            logoutPath = "";
        }
        if (!logoutPath.isEmpty() && !logoutPath.startsWith("/")) {
            throw new IllegalArgumentException("spring.bpmn.ui.logout-path must be empty or start with '/': "
                    + logoutPath);
        }
        if (refreshInterval == null) {
            refreshInterval = Duration.ZERO;
        }
        if (refreshInterval.isNegative()) {
            throw new IllegalArgumentException("spring.bpmn.ui.refresh-interval must not be negative: "
                    + refreshInterval);
        }
    }
}
