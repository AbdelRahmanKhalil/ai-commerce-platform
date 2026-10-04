package io.github.abdelrahmankhalil.aicommerce.catalog.internal;

/**
 * An invalid {@code page}/{@code size} query parameter was supplied (negative page,
 * or a size outside the supported range). Maps to HTTP 400.
 */
public class InvalidPagingParametersException extends RuntimeException {

    public InvalidPagingParametersException(String message) {
        super(message);
    }
}
