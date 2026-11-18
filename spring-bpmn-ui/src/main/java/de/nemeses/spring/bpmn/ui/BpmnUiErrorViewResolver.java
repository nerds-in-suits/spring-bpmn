package de.nemeses.spring.bpmn.ui;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.boot.webmvc.autoconfigure.error.ErrorViewResolver;
import org.springframework.core.Ordered;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.StreamUtils;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.View;
import org.springframework.web.util.HtmlUtils;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Error page of the UI instead of Spring Boot's "Whitelabel Error Page", for browser requests ({@code text/html})
 * below {@code /bpmn-ui} only, e.g. an unknown page {@code /bpmn-ui/foo} (404).
 * <p>
 * Spring Boot's {@code BasicErrorController} asks this resolver for every HTML error; it answers only if the original
 * request URI ({@link RequestDispatcher#ERROR_REQUEST_URI}) is {@code /bpmn-ui} or below it (after the servlet context
 * path), and returns {@code null} for all other paths, so the error handling of the rest of the application stays as
 * it is. JSON requests, such as those of the REST API, never reach it. It is ordered before Spring Boot's
 * {@code DefaultErrorViewResolver}, so it wins below {@code /bpmn-ui} over {@code error/<status>.html} pages of the
 * application.
 * <p>
 * The page is the template {@code de/nemeses/spring/bpmn/ui/error.html} of this JAR (not a static resource), styled by
 * the {@code app.css} of the UI, with the same header as the UI and a link back to the entry page {@code /bpmn-ui/}.
 * It shows the HTTP status code, its reason phrase and a fixed sentence for the status; it never shows exception
 * messages, stack traces or the error attributes of the model.
 */
class BpmnUiErrorViewResolver implements ErrorViewResolver, Ordered {

    /** The classpath location of the page template. */
    private static final String TEMPLATE = "de/nemeses/spring/bpmn/ui/error.html";

    private final String template;

    BpmnUiErrorViewResolver() {
        try {
            this.template = StreamUtils.copyToString(new ClassPathResource(TEMPLATE).getInputStream(),
                    StandardCharsets.UTF_8);
        } catch (final IOException e) {
            throw new UncheckedIOException("cannot read " + TEMPLATE, e);
        }
    }

    @Override
    public ModelAndView resolveErrorView(final HttpServletRequest request, final HttpStatus status,
            final Map<String, Object> model) {
        final String uiPath = request.getContextPath() + BpmnUiAutoConfiguration.PATH;
        if (request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI) instanceof final String uri
                && (uri.equals(uiPath) || uri.startsWith(uiPath + "/"))) {
            final ModelAndView view = new ModelAndView(new ErrorPageView(status));
            view.setStatus(status);
            return view;
        }
        return null;
    }

    /**
     * Before Spring Boot's {@code DefaultErrorViewResolver}, which has the lowest precedence.
     */
    @Override
    public int getOrder() {
        return 0;
    }

    /**
     * The sentence the page shows for a status.
     */
    static String describe(final HttpStatus status) {
        return switch (status) {
        case NOT_FOUND -> "The page you asked for does not exist.";
        case UNAUTHORIZED, FORBIDDEN -> "You are not allowed to open this page.";
        default -> status.is4xxClientError() ? "The request could not be handled."
                : "Something went wrong on the server. Please try again later.";
        };
    }

    /**
     * Renders the template with the servlet context path, the status and its description, all HTML-escaped.
     */
    private final class ErrorPageView implements View {

        private final HttpStatus status;

        ErrorPageView(final HttpStatus status) {
            this.status = status;
        }

        @Override
        public String getContentType() {
            return MediaType.TEXT_HTML_VALUE;
        }

        @Override
        public void render(final Map<String, ?> model, final HttpServletRequest request,
                final HttpServletResponse response) throws IOException {
            final String page = BpmnUiErrorViewResolver.this.template
                    .replace("{{contextPath}}", HtmlUtils.htmlEscape(request.getContextPath()))
                    .replace("{{status}}", String.valueOf(this.status.value()))
                    .replace("{{reason}}", HtmlUtils.htmlEscape(this.status.getReasonPhrase()))
                    .replace("{{description}}", HtmlUtils.htmlEscape(describe(this.status)));
            response.setContentType(MediaType.TEXT_HTML_VALUE + ";charset=UTF-8");
            response.getWriter().write(page);
        }
    }
}
