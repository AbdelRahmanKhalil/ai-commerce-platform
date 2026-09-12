package io.github.abdelrahmankhalil.aicommerce.tenancy.web;

import io.github.abdelrahmankhalil.aicommerce.tenancy.internal.UserAccountProvisioningService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Idempotent local UserAccount provisioning. The JWT is already validated by the
 * resource-server filter chain before this controller runs; no database write happens
 * inside that filter chain - only here, after authentication.
 */
@RestController
@RequestMapping("/api/me")
class MeController {

    private final UserAccountProvisioningService provisioningService;

    MeController(UserAccountProvisioningService provisioningService) {
        this.provisioningService = provisioningService;
    }

    @PostMapping
    ResponseEntity<MeResponse> provisionCurrentUser(@AuthenticationPrincipal Jwt jwt) {
        UUID userAccountId = provisioningService.findOrCreateUserAccount(jwt.getSubject());
        return ResponseEntity.ok(new MeResponse(userAccountId));
    }

    record MeResponse(UUID userAccountId) {
    }
}
