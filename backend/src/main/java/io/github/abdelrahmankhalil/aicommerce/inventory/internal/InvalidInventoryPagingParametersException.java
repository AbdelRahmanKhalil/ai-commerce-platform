package io.github.abdelrahmankhalil.aicommerce.inventory.internal;

/**
 * An invalid {@code page}/{@code size} query parameter was supplied (negative page,
 * or a size outside the supported range). Maps to HTTP 400.
 */
public class InvalidInventoryPagingParametersException extends RuntimeException {

    public InvalidInventoryPagingParametersException(String message) {
        super(message);
    }
}
