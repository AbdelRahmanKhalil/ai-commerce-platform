package io.github.abdelrahmankhalil.aicommerce.inventory;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The DATABASE INTEGRITY cases required by the task brief (ADR 007): the composite
 * foreign key, the per-Variant uniqueness constraint, and the non-negative quantity
 * check in {@code V3__inventory.sql} are the defense-in-depth backstop rejecting
 * invalid rows even if application logic were ever bypassed.
 */
class InventoryDatabaseIntegrityIT extends AbstractInventoryIntegrationTest {

    @Test
    void postgresRejectsAnInventoryItemPointingAtAVariantFromAnotherStore() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org DB Integrity Store");
        UUID storeAId = createStore(ownerSubject, organizationId, "store-a-" + UUID.randomUUID(), "EGP");
        UUID storeBId = createStore(ownerSubject, organizationId, "store-b-" + UUID.randomUUID(), "EGP");
        UUID variantInStoreA = createVariant(ownerSubject, organizationId, storeAId);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO inventory_item (id, organization_id, store_id, product_variant_id, on_hand_quantity, "
                        + "version, created_at, updated_at) VALUES (?, ?, ?, ?, 5, 0, ?, ?)",
                UUID.randomUUID(), organizationId, storeBId, variantInStoreA, OffsetDateTime.now(), OffsetDateTime.now()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void postgresRejectsAnInventoryItemPointingAtAVariantFromAnotherOrganization() throws Exception {
        String ownerASubject = provisionedSubject();
        UUID organizationAId = createOrganization(ownerASubject, "Org DB Integrity Org A");
        UUID storeAId = createStore(ownerASubject, organizationAId, "store-a-" + UUID.randomUUID(), "EGP");
        UUID variantInOrgA = createVariant(ownerASubject, organizationAId, storeAId);

        String ownerBSubject = provisionedSubject();
        UUID organizationBId = createOrganization(ownerBSubject, "Org DB Integrity Org B");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO inventory_item (id, organization_id, store_id, product_variant_id, on_hand_quantity, "
                        + "version, created_at, updated_at) VALUES (?, ?, ?, ?, 5, 0, ?, ?)",
                UUID.randomUUID(), organizationBId, storeAId, variantInOrgA, OffsetDateTime.now(), OffsetDateTime.now()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void postgresRejectsASecondInventoryItemForTheSameVariant() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org DB Integrity Unique");
        UUID storeId = createStore(ownerSubject, organizationId, "store-" + UUID.randomUUID(), "EGP");
        UUID variantId = createVariant(ownerSubject, organizationId, storeId);

        mockMvc.perform(put(inventoryUrl(organizationId, storeId, variantId))
                        .with(jwtSubject(ownerSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("onHandQuantity", 10))))
                .andExpect(status().isCreated());

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO inventory_item (id, organization_id, store_id, product_variant_id, on_hand_quantity, "
                        + "version, created_at, updated_at) VALUES (?, ?, ?, ?, 1, 0, ?, ?)",
                UUID.randomUUID(), organizationId, storeId, variantId, OffsetDateTime.now(), OffsetDateTime.now()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void postgresRejectsANegativeOnHandQuantity() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org DB Integrity Negative");
        UUID storeId = createStore(ownerSubject, organizationId, "store-" + UUID.randomUUID(), "EGP");
        UUID variantId = createVariant(ownerSubject, organizationId, storeId);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO inventory_item (id, organization_id, store_id, product_variant_id, on_hand_quantity, "
                        + "version, created_at, updated_at) VALUES (?, ?, ?, ?, -1, 0, ?, ?)",
                UUID.randomUUID(), organizationId, storeId, variantId, OffsetDateTime.now(), OffsetDateTime.now()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void scopedCompositeIdentityConstraintAllowsTheRealApplicationRowToBeInserted() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org DB Integrity Valid");
        UUID storeId = createStore(ownerSubject, organizationId, "store-" + UUID.randomUUID(), "EGP");
        UUID variantId = createVariant(ownerSubject, organizationId, storeId);

        mockMvc.perform(put(inventoryUrl(organizationId, storeId, variantId))
                        .with(jwtSubject(ownerSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("onHandQuantity", 42))))
                .andExpect(status().isCreated());

        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory_item WHERE product_variant_id = ? AND store_id = ? "
                        + "AND organization_id = ?",
                Long.class, variantId, storeId, organizationId);
        assertThat(count).isEqualTo(1);
    }
}
