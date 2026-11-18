package de.nemeses.spring.bpmn.rest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import jakarta.servlet.FilterChain;

/**
 * {@link BpmnRestMdcUserFilter}: the user is in the MDC during the request and removed afterwards.
 */
class BpmnRestMdcUserFilterTest {

    private final Collection<String> seen = new ArrayList<>();

    private final FilterChain chain = (request, response) -> this.seen.add(MDC.get(BpmnRestMdcUserFilter.MDC_USER));

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    private void filter(final Authentication authentication) throws Exception {
        SecurityContextHolder.getContext().setAuthentication(authentication);
        new BpmnRestMdcUserFilter().doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), this.chain);
    }

    @Test
    void testAuthenticatedUser() throws Exception {
        this.filter(UsernamePasswordAuthenticationToken.authenticated("alice", "secret",
                AuthorityUtils.NO_AUTHORITIES));
        Assertions.assertEquals(List.of("alice"), this.seen);
        Assertions.assertNull(MDC.get(BpmnRestMdcUserFilter.MDC_USER));
    }

    @Test
    void testRemovedAfterException() {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated("bob",
                "secret", AuthorityUtils.NO_AUTHORITIES));
        final FilterChain failing = (request, response) -> {
            throw new IllegalStateException("failed");
        };
        Assertions.assertThrows(IllegalStateException.class, () -> new BpmnRestMdcUserFilter()
                .doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), failing));
        Assertions.assertNull(MDC.get(BpmnRestMdcUserFilter.MDC_USER));
    }

    @Test
    void testUnauthenticated() throws Exception {
        this.filter(null);
        Assertions.assertEquals(Collections.singletonList(null), this.seen);
    }

    @Test
    void testAnonymous() throws Exception {
        this.filter(new AnonymousAuthenticationToken("key", "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        Assertions.assertEquals(Collections.singletonList(null), this.seen);
    }
}
