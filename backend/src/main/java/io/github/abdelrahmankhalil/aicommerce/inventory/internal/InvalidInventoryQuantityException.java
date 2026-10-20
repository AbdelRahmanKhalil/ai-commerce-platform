package io.github.abdelrahmankhalil.aicommerce.inventory.internal;

/**
 * {@code onHandQuantity} must never be negative (ADR 007) - enforced here at the
 * application/service boundary as well as by the database {@code CHECK} constraint,
 * so a future internal caller bypassing the controller's Bean Validation cannot
 * persist a negative quantity either. Maps to HTTP 400.
 */
public class InvalidInventoryQuantityException extends RuntimeException {

    public InvalidInventoryQuantityException(String message) {
        super(message);
    }
}
