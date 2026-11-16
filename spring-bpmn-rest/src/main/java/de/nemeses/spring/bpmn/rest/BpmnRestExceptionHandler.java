package de.nemeses.spring.bpmn.rest;

import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import de.nemeses.spring.bpmn.BpmnParseException;
import de.nemeses.spring.bpmn.exec.ProcessEngineException;
import de.nemeses.spring.bpmn.repository.NoSuchProcessDefinitionException;
import de.nemeses.spring.bpmn.repository.NoSuchProcessInstanceException;
import de.nemeses.spring.bpmn.repository.NoSuchProcessStepException;

/**
 * Maps the exceptions of the controllers of this package to RFC 9457 problem details:
 * <ul>
 * <li>{@code NoSuch*Exception} of the repository API, and a {@link ProcessEngineException} caused by one: {@code 404
 * Not Found};</li>
 * <li>any other {@link ProcessEngineException}: {@code 422 Unprocessable Content};</li>
 * <li>{@link BpmnParseException}, unreadable bodies and invalid parameters: {@code 400 Bad Request};</li>
 * <li>{@link ErrorResponseException}s (e.g. {@code ResponseStatusException}): their own status.</li>
 * </ul>
 * Applies only to the controllers of this package, so the error handling of the application is not changed.
 */
@RestControllerAdvice(basePackageClasses = BpmnRestExceptionHandler.class)
class BpmnRestExceptionHandler {

    @ExceptionHandler({ NoSuchProcessDefinitionException.class, NoSuchProcessInstanceException.class,
        NoSuchProcessStepException.class })
    ProblemDetail notFound(final Exception e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ProcessEngineException.class)
    ProblemDetail processEngine(final ProcessEngineException e) {
        final Throwable cause = e.getCause();
        if (cause instanceof NoSuchProcessDefinitionException || cause instanceof NoSuchProcessInstanceException
                || cause instanceof NoSuchProcessStepException) {
            return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, cause.getMessage());
        }
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
    }

    @ExceptionHandler(BpmnParseException.class)
    ProblemDetail invalidBpmn(final BpmnParseException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler({ HttpMessageNotReadableException.class, ServletRequestBindingException.class,
        TypeMismatchException.class })
    ProblemDetail badRequest(final Exception e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(ErrorResponseException.class)
    ResponseEntity<ProblemDetail> errorResponse(final ErrorResponseException e) {
        return ResponseEntity.of(e.getBody()).build();
    }
}
