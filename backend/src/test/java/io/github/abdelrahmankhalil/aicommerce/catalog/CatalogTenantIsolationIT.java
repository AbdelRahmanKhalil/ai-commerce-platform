package io.github.abdelrahmankhalil.aicommerce.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The TENANT ISOLATION cases required by the task brief/CLAUDE.md/ADR 004: a user
 * from a different Organization can neither read nor mutate another Organization's
 * catalog, and a MEMBER whose StoreAccess is scoped to one Store cannot read or
 * mutate another Store's catalog in the same Organization.
 */
class CatalogTenantIsolationIT extends AbstractCatalogIntegrationTest {

    @Test
    void ownerOfAnotherOrganizationCannotReadAForeignStoresCatalog() throws Exception {
        String ownerASubject = provisionedSubject();
        UUID organizationAId = createOrganization(ownerASubject, "Org A");
        UUID storeAId = createStore(ownerASubject, organizationAId, "store-a-" + UUID.randomUUID(), "EGP");
        createCategory(ownerASubject, organizationAId, storeAId, "New Arrivals", "new-arrivals");

        String ownerBSubject = provisionedSubject();
        UUID organizationBId = createOrganization(ownerBSubject, "Org B");

        mockMvc.perform(get(categoriesUrl(organizationBId, storeAId))
                        .with(jwtSubject(ownerBSubject)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(productsUrl(organizationBId, storeAId))
                        .with(jwtSubject(ownerBSubject)))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownerOfAnotherOrganizationCannotMutateAForeignStoresCatalog() throws Exception {
        String ownerASubject = provisionedSubject();
        UUID organizationAId = createOrganization(ownerASubject, "Org A2");
        UUID storeAId = createStore(ownerASubject, organizationAId, "store-a2-" + UUID.randomUUID(), "EGP");

        String ownerBSubject = provisionedSubject();
        UUID organizationBId = createOrganization(ownerBSubject, "Org B2");

        mockMvc.perform(post(categoriesUrl(organizationBId, storeAId))
                        .with(jwtSubject(ownerBSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Intruder", "slug", "intruder"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(productsUrl(organizationBId, storeAId))
                        .with(jwtSubject(ownerBSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(simpleProductBody("Intruder Product", "intruder-product"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void memberWithStoreAccessToOneStoreCannotReadAnotherStoreInSameOrganization() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org C");
        UUID storeAId = createStore(ownerSubject, organizationId, "store-c-a-" + UUID.randomUUID(), "EGP");
        UUID storeBId = createStore(ownerSubject, organizationId, "store-c-b-" + UUID.randomUUID(), "EGP");
        createCategory(ownerSubject, organizationId, storeBId, "New Arrivals", "new-arrivals");

        String memberSubject = provisionedSubject();
        UUID memberUserAccountId = addMember(memberSubject, organizationId);
        grantStoreAccess(ownerSubject, organizationId, storeAId, memberUserAccountId, "CATALOG_EDITOR");

        mockMvc.perform(get(categoriesUrl(organizationId, storeBId))
                        .with(jwtSubject(memberSubject)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(productsUrl(organizationId, storeBId))
                        .with(jwtSubject(memberSubject)))
                .andExpect(status().isForbidden());
    }

    @Test
    void memberWithStoreAccessToOneStoreCannotMutateAnotherStoreInSameOrganization() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org D");
        UUID storeAId = createStore(ownerSubject, organizationId, "store-d-a-" + UUID.randomUUID(), "EGP");
        UUID storeBId = createStore(ownerSubject, organizationId, "store-d-b-" + UUID.randomUUID(), "EGP");

        String memberSubject = provisionedSubject();
        UUID memberUserAccountId = addMember(memberSubject, organizationId);
        grantStoreAccess(ownerSubject, organizationId, storeAId, memberUserAccountId, "CATALOG_EDITOR");

        mockMvc.perform(post(categoriesUrl(organizationId, storeBId))
                        .with(jwtSubject(memberSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Intruder", "slug", "intruder"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(productsUrl(organizationId, storeBId))
                        .with(jwtSubject(memberSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(simpleProductBody("Intruder Product", "intruder-product"))))
                .andExpect(status().isForbidden());
    }
}
