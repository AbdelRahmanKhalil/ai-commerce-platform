package io.github.abdelrahmankhalil.aicommerce.support.web;

import io.github.abdelrahmankhalil.aicommerce.tenancy.MembershipNotFoundException;
import io.github.abdelrahmankhalil.aicommerce.tenancy.TenancyAccessDeniedException;
import io.github.abdelrahmankhalil.aicommerce.tenancy.UserAccountNotProvisionedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Central RFC 9457 (ProblemDetail) error mapping. Extends Spring's own
 * {@link ResponseEntityExceptionHandler}, which already produces ProblemDetail
 * responses for standard Spring MVC exceptions (validation errors, malformed
 * requests, etc.) - only the exceptions specific to this application are added here.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(TenancyAccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(TenancyAccessDeniedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        problem.setTitle("Access denied");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
    }

    @ExceptionHandler(UserAccountNotProvisionedException.class)
    public ResponseEntity<ProblemDetail> handleNotProvisioned(UserAccountNotProvisionedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        problem.setTitle("User account not provisioned");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
    }

    @ExceptionHandler(MembershipNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleMembershipNotFound(MembershipNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Membership not found");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "The request conflicts with an existing resource.");
        problem.setTitle("Conflict");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }
}
