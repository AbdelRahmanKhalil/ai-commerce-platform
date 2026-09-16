package io.github.abdelrahmankhalil.aicommerce.catalog;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The AUTHORIZATION cases required by the task brief: OWNER/ADMIN (Organization-wide)
 * and MANAGER/CATALOG_EDITOR (Store-scoped) may write catalog data; ORDER_MANAGER and
 * SUPPORT_AGENT may read but never write.
 */
class CatalogAuthorizationIT extends AbstractCatalogIntegrationTest {

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN"})
    void organizationWideRolesCanWriteCatalog(String organizationRole) throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org " + organizationRole);
        UUID storeId = createStore(ownerSubject, organizationId, "store-" + UUID.randomUUID(), "EGP");

        String staffSubject = provisionedSubject();
        UUID staffUserAccountId = userAccountId(staffSubject);
        io.github.abdelrahmankhalil.aicommerce.support.TenancyFixtures.addMembership(
                jdbcTemplate, staffUserAccountId, organizationId, organizationRole);

        mockMvc.perform(post(categoriesUrl(organizationId, storeId))
                        .with(jwtSubject(staffSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "New Arrivals", "slug", "new-arrivals"))))
                .andExpect(status().isCreated());
    }

    @org.junit.jupiter.api.Test
    void ownerCanWriteCatalog() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org Owner");
        UUID storeId = createStore(ownerSubject, organizationId, "store-" + UUID.randomUUID(), "EGP");

        mockMvc.perform(post(categoriesUrl(organizationId, storeId))
                        .with(jwtSubject(ownerSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "New Arrivals", "slug", "new-arrivals"))))
                .andExpect(status().isCreated());
    }

    @ParameterizedTest
    @ValueSource(strings = {"MANAGER", "CATALOG_EDITOR"})
    void storeScopedWriteRolesCanWriteCatalog(String storeRole) throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org " + storeRole);
        UUID storeId = createStore(ownerSubject, organizationId, "store-" + UUID.randomUUID(), "EGP");

        String memberSubject = provisionedSubject();
        UUID memberUserAccountId = addMember(memberSubject, organizationId);
        grantStoreAccess(ownerSubject, organizationId, storeId, memberUserAccountId, storeRole);

        mockMvc.perform(post(categoriesUrl(organizationId, storeId))
                        .with(jwtSubject(memberSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "New Arrivals", "slug", "new-arrivals"))))
                .andExpect(status().isCreated());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ORDER_MANAGER", "SUPPORT_AGENT"})
    void readOnlyStoreRolesCannotWriteCatalog(String storeRole) throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org RO " + storeRole);
        UUID storeId = createStore(ownerSubject, organizationId, "store-" + UUID.randomUUID(), "EGP");

        String memberSubject = provisionedSubject();
        UUID memberUserAccountId = addMember(memberSubject, organizationId);
        grantStoreAccess(ownerSubject, organizationId, storeId, memberUserAccountId, storeRole);

        mockMvc.perform(post(categoriesUrl(organizationId, storeId))
                        .with(jwtSubject(memberSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "New Arrivals", "slug", "new-arrivals"))))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ORDER_MANAGER", "SUPPORT_AGENT"})
    void readOnlyStoreRolesCanReadCatalog(String storeRole) throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org Read " + storeRole);
        UUID storeId = createStore(ownerSubject, organizationId, "store-" + UUID.randomUUID(), "EGP");
        createCategory(ownerSubject, organizationId, storeId, "New Arrivals", "new-arrivals");

        String memberSubject = provisionedSubject();
        UUID memberUserAccountId = addMember(memberSubject, organizationId);
        grantStoreAccess(ownerSubject, organizationId, storeId, memberUserAccountId, storeRole);

        mockMvc.perform(get(categoriesUrl(organizationId, storeId))
                        .with(jwtSubject(memberSubject)))
                .andExpect(status().isOk());

        mockMvc.perform(get(productsUrl(organizationId, storeId))
                        .with(jwtSubject(memberSubject)))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ORDER_MANAGER", "SUPPORT_AGENT"})
    void readOnlyStoreRolesCannotUpdateOrDeleteCategory(String storeRole) throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org RW " + storeRole);
        UUID storeId = createStore(ownerSubject, organizationId, "store-" + UUID.randomUUID(), "EGP");
        UUID categoryId = createCategory(ownerSubject, organizationId, storeId, "New Arrivals", "new-arrivals");

        String memberSubject = provisionedSubject();
        UUID memberUserAccountId = addMember(memberSubject, organizationId);
        grantStoreAccess(ownerSubject, organizationId, storeId, memberUserAccountId, storeRole);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put(categoriesUrl(organizationId, storeId) + "/{categoryId}", categoryId)
                        .with(jwtSubject(memberSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Renamed", "slug", "renamed"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete(categoriesUrl(organizationId, storeId) + "/{categoryId}", categoryId)
                        .with(jwtSubject(memberSubject)))
                .andExpect(status().isForbidden());
    }
}
