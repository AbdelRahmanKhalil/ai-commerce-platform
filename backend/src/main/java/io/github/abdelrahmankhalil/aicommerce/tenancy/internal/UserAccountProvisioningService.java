package io.github.abdelrahmankhalil.aicommerce.tenancy.internal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Single responsibility: turning a validated external (Keycloak) subject into a
 * local UserAccount, idempotently and race-safely. See
 * {@code POST /api/me} in {@code tenancy.web.MeController}.
 */
@Service
public class UserAccountProvisioningService {

    private final UserAccountRepository userAccountRepository;

    UserAccountProvisioningService(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @Transactional
    public UUID findOrCreateUserAccount(String externalSubject) {
        UUID candidateId = UUID.randomUUID();
        userAccountRepository.insertIfAbsent(candidateId, externalSubject);
        return userAccountRepository.findByKeycloakSubject(externalSubject)
                .map(UserAccount::getId)
                .orElseThrow(() -> new IllegalStateException(
                        "UserAccount provisioning for subject '" + externalSubject
                                + "' inserted no row and found none - this should be unreachable."));
    }
}
