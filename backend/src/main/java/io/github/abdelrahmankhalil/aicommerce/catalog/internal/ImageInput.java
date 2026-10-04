package io.github.abdelrahmankhalil.aicommerce.catalog.internal;

/**
 * An image supplied as part of Product creation/update, in client-supplied order.
 * The stored {@code position} is derived from this order - clients never supply a
 * position directly (ADR 006).
 */
public record ImageInput(String url, String altText) {
}
