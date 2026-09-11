package io.github.abdelrahmankhalil.aicommerce.tenancy;

/**
 * No local UserAccount exists yet for the given external (Keycloak) subject. Callers
 * are expected to have already invoked the {@code POST /api/me} provisioning endpoint
 * before relying on any other tenancy-scoped operation. Maps to HTTP 401.
 */
public class UserAccountNotProvisionedException extends RuntimeException {

    public UserAccountNotProvisionedException(String externalSubject) {
        super("No UserAccount is provisioned for subject '" + externalSubject
                + "'. Call POST /api/me first.");
    }
}
