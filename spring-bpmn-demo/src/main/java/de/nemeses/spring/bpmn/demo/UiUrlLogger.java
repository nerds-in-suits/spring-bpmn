package de.nemeses.spring.bpmn.demo;

import org.slf4j.LoggerFactory;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.boot.web.server.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import de.nemeses.spring.bpmn.rest.BpmnRestProperties;

/**
 * Logs the URLs of the web UI and the REST API once the web server listens, with the actual port and the servlet
 * context path.
 */
@Component
class UiUrlLogger implements ApplicationListener<WebServerInitializedEvent> {

    private final BpmnRestProperties restProperties;

    UiUrlLogger(final BpmnRestProperties restProperties) {
        this.restProperties = restProperties;
    }

    @Override
    public void onApplicationEvent(final WebServerInitializedEvent event) {
        final WebServerApplicationContext context = event.getApplicationContext();
        final String contextPath = context.getEnvironment().getProperty("server.servlet.context-path", "");
        final String root = "http://localhost:" + event.getWebServer().getPort() + contextPath;
        LoggerFactory.getLogger(this.getClass()).info("BPMN UI: {}/bpmn-ui/ - REST API: {}{}", root, root,
                this.restProperties.basePath());
    }
}
