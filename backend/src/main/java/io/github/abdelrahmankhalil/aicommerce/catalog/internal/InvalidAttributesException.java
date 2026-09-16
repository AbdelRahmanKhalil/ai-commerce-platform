package io.github.abdelrahmankhalil.aicommerce.catalog.internal;

/**
 * A ProductVariant's {@code attributes} map contained a blank key or a null value.
 * {@code attributes} must be a flat String -> String map (never nested/array values,
 * which Jackson already rejects while deserializing into {@code Map<String,String>}).
 * Maps to HTTP 400.
 */
public class InvalidAttributesException extends RuntimeException {

    public InvalidAttributesException() {
        super("Variant attributes must be a flat map of non-blank keys to non-null string values");
    }
}
