package io.github.abdelrahmankhalil.aicommerce.tenancy.internal;

/**
 * Store creation targeted a deployment-reserved slug (ADR 008). This is an
 * application/domain-policy rejection, distinct from malformed reserved-slug
 * *configuration* itself, which fails fast as {@link IllegalArgumentException} during
 * properties binding/application startup instead - see
 * {@link ReservedStoreSlugProperties}.
 * <p>
 * Not part of {@code tenancy}'s public API - thrown only by
 * {@link ReservedStoreSlugPolicy} and caught by {@code tenancy.web}'s own exception
 * handler, both in this module, mirroring {@link MembershipNotFoundException}. The
 * message deliberately does not echo the rejected slug back; HTTP mapping must stay
 * sanitized (see {@code TenancyExceptionHandler}).
 */
public class ReservedStoreSlugException extends RuntimeException {

    public ReservedStoreSlugException() {
        super("Store slug is reserved and cannot be used");
    }
}
