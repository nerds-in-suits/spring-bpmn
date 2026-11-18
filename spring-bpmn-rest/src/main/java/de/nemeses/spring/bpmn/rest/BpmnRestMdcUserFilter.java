package de.nemeses.spring.bpmn.rest;

import java.io.IOException;

import org.slf4j.MDC;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Puts the name of the authenticated user of a REST API request into the SLF4J {@link MDC} under
 * {@value #MDC_USER} while the rest of the security filter chain and the controller handle the request; the key is
 * removed afterwards, so it never leaks to the next request of a pooled thread.
 * <p>
 * {@link BpmnRestSecurityConfiguration} adds the filter to {@code bpmnRestSecurityFilterChain} after the
 * authentication filters (also those of {@link BpmnRestHttpSecurityCustomizer}s) and before the authorization, so
 * log lines of rejected requests carry the user as well. Requests without authentication or with an anonymous one get
 * no key. An application that replaces the chain adds the filter itself if it wants the key.
 */
class BpmnRestMdcUserFilter extends OncePerRequestFilter {

    /** {@link MDC} key of the name of the authenticated user of the request. */
    static final String MDC_USER = "user";

    private final SecurityContextHolderStrategy securityContextHolderStrategy = SecurityContextHolder
            .getContextHolderStrategy();

    private final AuthenticationTrustResolver trustResolver = new AuthenticationTrustResolverImpl();

    @Override
    protected void doFilterInternal(final HttpServletRequest request, final HttpServletResponse response,
            final FilterChain filterChain) throws ServletException, IOException {
        final Authentication authentication = this.securityContextHolderStrategy.getContext().getAuthentication();
        if (!this.trustResolver.isAuthenticated(authentication)) {
            filterChain.doFilter(request, response);
            return;
        }
        try (MDC.MDCCloseable _ = MDC.putCloseable(BpmnRestMdcUserFilter.MDC_USER, authentication.getName())) {
            filterChain.doFilter(request, response);
        }
    }
}
