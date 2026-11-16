package de.nemeses.spring.bpmn.demo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

/**
 * The fixed users of the demo, with their password equal to their name:
 * <ul>
 * <li>{@code viewer}: {@code BPMN_READ}, may only read definitions, instances and user tasks;</li>
 * <li>{@code admin}: all authorities of the REST API ({@code BPMN_READ}, {@code BPMN_DEPLOY}, {@code BPMN_START},
 * {@code BPMN_VARIABLES}, {@code BPMN_TASK}).</li>
 * </ul>
 * {@code application.yaml} maps the operations of the REST API to these authorities
 * ({@code spring.bpmn.rest.security.authority.*}). Spring Boot's default security filter chain secures the UI with its
 * form login; the REST API accepts that login and HTTP Basic.
 */
@Configuration(proxyBeanMethods = false)
class DemoUsers {

    @Bean
    UserDetailsService userDetailsService() {
        return new InMemoryUserDetailsManager(
                User.withUsername("viewer").password("{noop}viewer").authorities("BPMN_READ").build(),
                User.withUsername("admin").password("{noop}admin")
                        .authorities("BPMN_READ", "BPMN_DEPLOY", "BPMN_START", "BPMN_VARIABLES", "BPMN_TASK").build());
    }
}
