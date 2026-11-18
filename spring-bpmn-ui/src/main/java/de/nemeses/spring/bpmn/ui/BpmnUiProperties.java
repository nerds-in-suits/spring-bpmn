package de.nemeses.spring.bpmn.ui;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration properties of the BPMN UI ({@code spring.bpmn.ui.*}).
 *
 * @param logoutPath path of the application's logout below the servlet context path
 *            ({@code spring.bpmn.ui.logout-path}), linked from the header of the pages; empty hides the link, otherwise
 *            it must start with {@code /}. The default {@code /logout} is the logout of Spring Security, whose
 *            {@code GET} shows a confirmation page that logs out with a {@code POST} and its CSRF token.
 */
@ConfigurationProperties(prefix = "spring.bpmn.ui")
public record BpmnUiProperties(@DefaultValue("/logout") String logoutPath) {

    /**
     * Validates the properties; a missing logout path is read as empty.
     */
    public BpmnUiProperties {
        if (logoutPath == null) {
            logoutPath = "";
        }
        if (!logoutPath.isEmpty() && !logoutPath.startsWith("/")) {
            throw new IllegalArgumentException("spring.bpmn.ui.logout-path must be empty or start with '/': "
                    + logoutPath);
        }
    }
}
