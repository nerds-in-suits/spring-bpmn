package de.nemeses.spring.bpmn.rest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Answers rejected requests of the REST API with RFC 9457 problem details instead of a login redirect:
 * {@code 401 Unauthorized} without authentication, {@code 403 Forbidden} without the required authority or with a
 * missing or invalid CSRF token.
 * <p>
 * The {@code 401} response has no {@code WWW-Authenticate} header, so browsers do not open their HTTP Basic login
 * dialog for requests of the UI; clients send HTTP Basic credentials preemptively.
 */
class BpmnRestSecurityProblems implements AuthenticationEntryPoint, AccessDeniedHandler {

    @Override
    public void commence(final HttpServletRequest request, final HttpServletResponse response,
            final AuthenticationException authException) throws IOException {
        BpmnRestSecurityProblems.write(response, HttpStatus.UNAUTHORIZED, "Authentication required");
    }

    @Override
    public void handle(final HttpServletRequest request, final HttpServletResponse response,
            final AccessDeniedException accessDeniedException) throws IOException {
        BpmnRestSecurityProblems.write(response, HttpStatus.FORBIDDEN, "Access denied");
    }

    private static void write(final HttpServletResponse response, final HttpStatus status, final String detail)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"" + status.getReasonPhrase()
                + "\",\"status\":" + status.value() + ",\"detail\":\"" + detail + "\"}");
    }
}
