package io.github.abdelrahmankhalil.aicommerce.tenancy.web;

import io.github.abdelrahmankhalil.aicommerce.tenancy.TenancyAuthorization;
import io.github.abdelrahmankhalil.aicommerce.tenancy.internal.OrganizationManagementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/organizations")
class OrganizationController {

    private final OrganizationManagementService organizationManagementService;
    private final TenancyAuthorization tenancyAuthorization;

    OrganizationController(OrganizationManagementService organizationManagementService,
                            TenancyAuthorization tenancyAuthorization) {
        this.organizationManagementService = organizationManagementService;
        this.tenancyAuthorization = tenancyAuthorization;
    }

    @PostMapping
    ResponseEntity<CreateOrganizationResponse> createOrganization(@AuthenticationPrincipal Jwt jwt,
                                                                    @Valid @RequestBody CreateOrganizationRequest request) {
        UUID userAccountId = tenancyAuthorization.resolveUserAccountId(jwt.getSubject());
        UUID organizationId = organizationManagementService.createOrganization(request.name(), userAccountId);
        return ResponseEntity.status(HttpStatus.CREATED).body(new CreateOrganizationResponse(organizationId));
    }

    record CreateOrganizationRequest(
            @NotBlank @Size(max = 200) String name) {
    }

    record CreateOrganizationResponse(UUID organizationId) {
    }
}
