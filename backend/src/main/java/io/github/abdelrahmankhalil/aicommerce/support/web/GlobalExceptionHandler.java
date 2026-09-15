package io.github.abdelrahmankhalil.aicommerce.support.web;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Central RFC 9457 (ProblemDetail) error mapping for genuinely generic, module-agnostic
 * cases. Extends Spring's own {@link ResponseEntityExceptionHandler}, which already
 * produces ProblemDetail responses for standard Spring MVC exceptions (validation
 * errors, malformed requests, etc.). Module-specific exceptions are mapped by that
 * module's own {@code web} package (e.g. {@code tenancy.web.TenancyExceptionHandler}) -
 * this shared/support module deliberately never imports a feature module's types.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "The request conflicts with an existing resource.");
        problem.setTitle("Conflict");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }
}
