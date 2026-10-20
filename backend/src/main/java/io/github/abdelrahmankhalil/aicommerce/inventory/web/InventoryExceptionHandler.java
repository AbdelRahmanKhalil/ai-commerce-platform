package io.github.abdelrahmankhalil.aicommerce.inventory.web;

import io.github.abdelrahmankhalil.aicommerce.inventory.internal.InvalidInventoryPagingParametersException;
import io.github.abdelrahmankhalil.aicommerce.inventory.internal.InvalidInventoryQuantityException;
import io.github.abdelrahmankhalil.aicommerce.inventory.internal.InventoryVariantNotFoundException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * ProblemDetail mapping for this module's own exceptions, kept inside {@code
 * inventory} rather than the shared {@code support} module's global handler - mirrors
 * {@code catalog.web.CatalogExceptionHandler}/{@code tenancy.web.TenancyExceptionHandler}.
 * Cross-tenant/unauthorized access never reaches this handler: {@code
 * InventoryService} checks {@code TenancyAuthorization} before any Catalog lookup or
 * InventoryItem read/write, so those cases surface as {@code
 * TenancyAccessDeniedException} (403), handled globally by {@code
 * TenancyExceptionHandler}.
 * <p>
 * The first-initialization unique-constraint conflict is already handled by the
 * shared {@code GlobalExceptionHandler}'s {@code DataIntegrityViolationException} ->
 * 409 mapping - Inventory adds only the smallest mapping it genuinely needs beyond
 * that: an existing-row optimistic-locking conflict ({@code
 * OptimisticLockingFailureException}, ADR 007), which is a {@code
 * ConcurrencyFailureException} rather than a {@code DataIntegrityViolationException}
 * and so is not already covered.
 */
@RestControllerAdvice
class InventoryExceptionHandler {

    @ExceptionHandler(InventoryVariantNotFoundException.class)
    ResponseEntity<ProblemDetail> handleVariantNotFound(InventoryVariantNotFoundException ex) {
        return notFound("Product variant not found");
    }

    @ExceptionHandler(InvalidInventoryQuantityException.class)
    ResponseEntity<ProblemDetail> handleInvalidQuantity(InvalidInventoryQuantityException ex) {
        return badRequest("Invalid inventory quantity", ex.getMessage());
    }

    @ExceptionHandler(InvalidInventoryPagingParametersException.class)
    ResponseEntity<ProblemDetail> handleInvalidPaging(InvalidInventoryPagingParametersException ex) {
        return badRequest("Invalid paging parameters", ex.getMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> handleOptimisticLockingFailure(OptimisticLockingFailureException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "This inventory record was modified by another request. Reload and try again.");
        problem.setTitle("Conflict");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    private static ResponseEntity<ProblemDetail> notFound(String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, detail);
        problem.setTitle("Not found");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    private static ResponseEntity<ProblemDetail> badRequest(String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle(title);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }
}
