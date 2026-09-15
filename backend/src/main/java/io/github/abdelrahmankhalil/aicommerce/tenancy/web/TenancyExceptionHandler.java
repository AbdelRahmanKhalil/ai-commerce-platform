package io.github.abdelrahmankhalil.aicommerce.tenancy.web;

import io.github.abdelrahmankhalil.aicommerce.tenancy.TenancyAccessDeniedException;
import io.github.abdelrahmankhalil.aicommerce.tenancy.UserAccountNotProvisionedException;
import io.github.abdelrahmankhalil.aicommerce.tenancy.internal.InvalidStoreAccessGrantException;
import io.github.abdelrahmankhalil.aicommerce.tenancy.internal.MembershipNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * ProblemDetail mapping for this module's own exceptions. Kept inside {@code tenancy}
 * rather than the shared {@code support} module's global handler, so {@code support}
 * never needs to import a feature module's types - each module is responsible for
 * mapping its own domain exceptions to HTTP responses. {@code support}'s
 * {@code GlobalExceptionHandler} remains for genuinely generic, module-agnostic cases.
 */
@RestControllerAdvice
class TenancyExceptionHandler {

    @ExceptionHandler(TenancyAccessDeniedException.class)
    ResponseEntity<ProblemDetail> handleAccessDenied(TenancyAccessDeniedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
                "You do not have access to this resource.");
        problem.setTitle("Access denied");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
    }

    @ExceptionHandler(UserAccountNotProvisionedException.class)
    ResponseEntity<ProblemDetail> handleNotProvisioned(UserAccountNotProvisionedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
                "This account has not completed provisioning. Call POST /api/me first.");
        problem.setTitle("User account not provisioned");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
    }

    @ExceptionHandler(MembershipNotFoundException.class)
    ResponseEntity<ProblemDetail> handleMembershipNotFound(MembershipNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "The specified user is not a member of this Organization.");
        problem.setTitle("Membership not found");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(InvalidStoreAccessGrantException.class)
    ResponseEntity<ProblemDetail> handleInvalidStoreAccessGrant(InvalidStoreAccessGrantException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "This user already has Organization-wide Store access and cannot be granted "
                        + "a Store-scoped role.");
        problem.setTitle("Invalid StoreAccess grant");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }
}
