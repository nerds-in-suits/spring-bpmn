package de.nemeses.spring.bpmn.rest;

import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;

/**
 * Adapts the security filter chain {@code bpmnRestSecurityFilterChain} of the REST API, e.g. to accept bearer tokens
 * with {@code http.oauth2ResourceServer(...)}.
 * <p>
 * All beans of this type are applied in their order after the chain is configured from
 * {@link BpmnRestProperties.Security}, before it is built. The authorization rules of the operations are already set
 * then and cannot be extended; to change them, replace the bean {@code bpmnRestSecurityFilterChain} instead.
 */
@FunctionalInterface
public interface BpmnRestHttpSecurityCustomizer extends Customizer<HttpSecurity> {
}
