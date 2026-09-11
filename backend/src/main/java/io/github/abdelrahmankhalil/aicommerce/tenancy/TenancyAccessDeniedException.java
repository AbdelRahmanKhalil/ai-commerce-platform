package io.github.abdelrahmankhalil.aicommerce.tenancy;

/**
 * The calling UserAccount does not have the required Organization or Store access.
 * Maps to HTTP 403.
 */
public class TenancyAccessDeniedException extends RuntimeException {

    public TenancyAccessDeniedException(String message) {
        super(message);
    }
}
