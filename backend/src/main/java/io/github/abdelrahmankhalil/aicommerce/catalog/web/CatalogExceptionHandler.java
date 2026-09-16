package io.github.abdelrahmankhalil.aicommerce.catalog.web;

import io.github.abdelrahmankhalil.aicommerce.catalog.internal.CategoryNotFoundException;
import io.github.abdelrahmankhalil.aicommerce.catalog.internal.InvalidAttributesException;
import io.github.abdelrahmankhalil.aicommerce.catalog.internal.InvalidPagingParametersException;
import io.github.abdelrahmankhalil.aicommerce.catalog.internal.ProductNotFoundException;
import io.github.abdelrahmankhalil.aicommerce.catalog.internal.VariantNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * ProblemDetail mapping for this module's own exceptions, kept inside {@code catalog}
 * rather than the shared {@code support} module's global handler - mirrors
 * {@code tenancy.web.TenancyExceptionHandler}. Cross-tenant/unauthorized access never
 * reaches this handler: every catalog application service checks
 * {@code TenancyAuthorization} before loading any Category/Product/Variant, so those
 * cases surface as {@code TenancyAccessDeniedException} (403), handled globally by
 * {@code TenancyExceptionHandler}, without ever revealing whether the resource exists.
 */
@RestControllerAdvice
class CatalogExceptionHandler {

    @ExceptionHandler(CategoryNotFoundException.class)
    ResponseEntity<ProblemDetail> handleCategoryNotFound(CategoryNotFoundException ex) {
        return notFound("Category not found");
    }

    @ExceptionHandler(ProductNotFoundException.class)
    ResponseEntity<ProblemDetail> handleProductNotFound(ProductNotFoundException ex) {
        return notFound("Product not found");
    }

    @ExceptionHandler(VariantNotFoundException.class)
    ResponseEntity<ProblemDetail> handleVariantNotFound(VariantNotFoundException ex) {
        return notFound("Product variant not found");
    }

    @ExceptionHandler(InvalidAttributesException.class)
    ResponseEntity<ProblemDetail> handleInvalidAttributes(InvalidAttributesException ex) {
        return badRequest("Invalid variant attributes", ex.getMessage());
    }

    @ExceptionHandler(InvalidPagingParametersException.class)
    ResponseEntity<ProblemDetail> handleInvalidPaging(InvalidPagingParametersException ex) {
        return badRequest("Invalid paging parameters", ex.getMessage());
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
