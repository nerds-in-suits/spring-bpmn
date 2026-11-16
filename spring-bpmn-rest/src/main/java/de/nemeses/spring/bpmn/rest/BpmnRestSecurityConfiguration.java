package de.nemeses.spring.bpmn.rest;

import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;

/**
 * Secures the REST API with the security filter chain {@code bpmnRestSecurityFilterChain} for
 * {@code <base-path>/**}, if Spring Security is on the classpath and {@code spring.bpmn.rest.security.enabled} is not
 * {@code false}.
 * <ul>
 * <li>Authentication is not the module's own: the chain takes the security context from the HTTP session, so the login
 * the application configures in its own chain (or Spring Boot's default chain) also authenticates the UI's requests;
 * HTTP Basic is accepted unless {@code spring.bpmn.rest.security.http-basic=false}; further mechanisms are added by
 * {@link BpmnRestHttpSecurityCustomizer} beans.</li>
 * <li>Authorization is per operation: each one requires the authority of its property
 * {@code spring.bpmn.rest.security.authority.<operation>} ({@link BpmnRestProperties.Authority}), or only an
 * authenticated user if that is empty. Any other request below the base path is denied, so an endpoint without
 * operation is never open.</li>
 * <li>Rejected requests get {@code 401} or {@code 403} problem details ({@link BpmnRestSecurityProblems}), never a
 * login redirect, and are not saved in the request cache.</li>
 * <li>CSRF protection uses Spring Security's single page application setup: the cookie {@code XSRF-TOKEN} is echoed in
 * the header {@code X-XSRF-TOKEN} by modifying requests. Requests with an {@code Authorization} header are exempt, as
 * they do not rely on ambient cookie credentials.</li>
 * </ul>
 * The chain is ordered before the application's unordered chains and Spring Boot's default chain, which keep securing
 * everything else (e.g. the UI). An application replaces it with its own bean named {@code bpmnRestSecurityFilterChain}.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = BpmnRestAutoConfiguration.SECURITY_CLASS)
@ConditionalOnBooleanProperty(name = "spring.bpmn.rest.security.enabled", matchIfMissing = true)
class BpmnRestSecurityConfiguration {

    /** The name of the security filter chain bean of the REST API. */
    static final String CHAIN = "bpmnRestSecurityFilterChain";

    /** The order of the chain: before the application's chains, which are mostly unordered. */
    static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 100;

    /**
     * @param httpSecurity the builder of Spring Security's web support
     * @param properties the properties
     * @param customizers the customizers to apply
     * @return the security filter chain of the REST API
     * @throws IllegalStateException if Spring Security's web support is not enabled or the chain cannot be built
     */
    @Bean(CHAIN)
    @ConditionalOnMissingBean(name = CHAIN)
    @Order(ORDER)
    @SuppressWarnings({"checkstyle:IllegalCatch", "PMD.AvoidCatchingGenericException"}) // build() throws Exception
    SecurityFilterChain bpmnRestSecurityFilterChain(final ObjectProvider<HttpSecurity> httpSecurity,
            final BpmnRestProperties properties, final ObjectProvider<BpmnRestHttpSecurityCustomizer> customizers) {
        final HttpSecurity http = httpSecurity.getIfAvailable();
        if (http == null) {
            throw new IllegalStateException("The BPMN REST API must be secured, but Spring Security's web support is"
                    + " not enabled (no HttpSecurity bean): enable it, e.g. with @EnableWebSecurity, or set"
                    + " spring.bpmn.rest.security.enabled=false explicitly to run the API without security");
        }
        try {
            return BpmnRestSecurityConfiguration.configure(http, properties, customizers).build();
        } catch (final Exception e) {
            throw new IllegalStateException("Cannot build the security filter chain of the BPMN REST API", e);
        }
    }

    /**
     * Configures the chain from the properties and applies the customizers.
     */
    private static HttpSecurity configure(final HttpSecurity http, final BpmnRestProperties properties,
            final ObjectProvider<BpmnRestHttpSecurityCustomizer> customizers) {
        final String base = properties.basePath();
        final BpmnRestProperties.Authority authority = properties.security().authority();
        final BpmnRestSecurityProblems problems = new BpmnRestSecurityProblems();
        http.securityMatcher(base + "/**")
                .authorizeHttpRequests(requests -> {
                    BpmnRestSecurityConfiguration.require(requests.requestMatchers(HttpMethod.GET,
                            base + "/process-definitions/**"), authority.readDefinitions());
                    BpmnRestSecurityConfiguration.require(requests.requestMatchers(HttpMethod.POST,
                            base + "/process-definitions"), authority.deploy());
                    BpmnRestSecurityConfiguration.require(requests.requestMatchers(HttpMethod.GET,
                            base + "/process-instances/**", base + "/user-tasks/**"), authority.readInstances());
                    BpmnRestSecurityConfiguration.require(requests.requestMatchers(HttpMethod.POST,
                            base + "/process-instances"), authority.startInstance());
                    for (final HttpMethod method : List.of(HttpMethod.PATCH, HttpMethod.PUT, HttpMethod.DELETE)) {
                        BpmnRestSecurityConfiguration.require(requests.requestMatchers(method,
                                base + "/process-instances/*/variables/**"), authority.updateVariables());
                    }
                    BpmnRestSecurityConfiguration.require(requests.requestMatchers(HttpMethod.POST,
                            base + "/user-tasks/*/complete"), authority.completeUserTask());
                    requests.anyRequest().denyAll();
                })
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(problems)
                        .accessDeniedHandler(problems))
                .requestCache(cache -> cache.disable())
                .csrf(csrf -> csrf.spa()
                        .ignoringRequestMatchers(request -> request.getHeader(HttpHeaders.AUTHORIZATION) != null));
        if (properties.security().httpBasic()) {
            http.httpBasic(basic -> basic.authenticationEntryPoint(problems));
        }
        for (final BpmnRestHttpSecurityCustomizer customizer : customizers.orderedStream().toList()) {
            customizer.customize(http);
        }
        return http;
    }

    /**
     * Requires the authority for the requests, or only authentication if it is empty.
     */
    private static void require(final AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizedUrl requests,
            final String authority) {
        if (StringUtils.hasText(authority)) {
            requests.hasAuthority(authority);
        } else {
            requests.authenticated();
        }
    }
}
