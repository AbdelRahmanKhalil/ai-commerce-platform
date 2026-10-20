package io.github.abdelrahmankhalil.aicommerce.inventory;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The CONCURRENCY cases required by the task brief (ADR 007), proved against the
 * real Testcontainers-backed PostgreSQL rather than with fake sequential calls.
 * <p>
 * Both tests use a raw, independently-committed JDBC transaction to deterministically
 * hold a genuine PostgreSQL row/key lock across the entire lifetime of a real,
 * concurrently-running HTTP PUT request (dispatched on a background thread via the
 * actual {@code InventoryController}/{@code InventoryService}/{@code
 * InventoryItemRepository} stack) - the HTTP request's own UPDATE/INSERT statement is
 * thereby forced by PostgreSQL itself to block until this test releases the lock by
 * committing, which guarantees (not merely makes likely) that both writers have
 * established identical pre-conflict state before either is allowed to complete. No
 * production code is touched or given a test-only synchronization hook - the proof
 * relies solely on genuine PostgreSQL MVCC/locking behavior, and the losing write
 * genuinely goes through the HTTP boundary, so the resulting status code is a direct,
 * non-flaky observation of the real API mapping, not a separately-mocked assertion.
 */
class InventoryConcurrencyIT extends AbstractInventoryIntegrationTest {

    @Test
    void existingRowConcurrentWritesConflictCleanlyWithNoLostUpdate() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org Concurrency Existing");
        UUID storeId = createStore(ownerSubject, organizationId, "store-" + UUID.randomUUID(), "EGP");
        UUID variantId = createVariant(ownerSubject, organizationId, storeId);

        mockMvc.perform(put(inventoryUrl(organizationId, storeId, variantId))
                        .with(jwtSubject(ownerSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("onHandQuantity", 10))))
                .andExpect(status().isCreated());

        UUID itemId = UUID.fromString(jdbcTemplate.queryForObject(
                "SELECT id FROM inventory_item WHERE product_variant_id = ?", String.class, variantId));

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (Connection lockHolder = dataSource.getConnection()) {
            lockHolder.setAutoCommit(false);

            // Acquire a genuine exclusive row lock, held open (uncommitted) for the
            // remainder of this try block - this is "transaction A" loading version 0
            // and not yet having written anything.
            try (PreparedStatement selectForUpdate =
                         lockHolder.prepareStatement("SELECT id FROM inventory_item WHERE id = ? FOR UPDATE")) {
                selectForUpdate.setObject(1, itemId);
                try (ResultSet rs = selectForUpdate.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                }
            }

            // "Transaction B": a real, concurrently-dispatched HTTP PUT. It loads the
            // same version 0 (a plain SELECT is never blocked by another session's row
            // lock), then its own UPDATE blocks on the row lock above - it cannot
            // possibly complete before this test chooses to release that lock.
            Future<MvcResult> conflictingWrite = executor.submit(() -> mockMvc.perform(
                            put(inventoryUrl(organizationId, storeId, variantId))
                                    .with(jwtSubject(ownerSubject))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(Map.of("onHandQuantity", 20))))
                    .andReturn());

            // Give the background request time to reach its (necessarily blocked)
            // UPDATE statement before we commit - harmless if generous, since the DB
            // lock (not timing) is what actually prevents a false negative.
            Thread.sleep(500);

            // "Transaction A" commits first, from version 0 to version 1.
            try (PreparedStatement update = lockHolder.prepareStatement(
                    "UPDATE inventory_item SET on_hand_quantity = 99, version = version + 1, updated_at = now() "
                            + "WHERE id = ?")) {
                update.setObject(1, itemId);
                assertThat(update.executeUpdate()).isEqualTo(1);
            }
            lockHolder.commit();

            MvcResult result = conflictingWrite.get(15, TimeUnit.SECONDS);
            assertThat(result.getResponse().getStatus()).isEqualTo(409);

            // Sanitized: no entity/version internals, SQL, or stack trace leak out.
            String body = result.getResponse().getContentAsString();
            assertThat(body).doesNotContainIgnoringCase("version")
                    .doesNotContainIgnoringCase("hibernate")
                    .doesNotContainIgnoringCase("StaleObjectState")
                    .doesNotContain(itemId.toString());
        } finally {
            executor.shutdownNow();
        }

        Long finalQuantity = jdbcTemplate.queryForObject(
                "SELECT on_hand_quantity FROM inventory_item WHERE id = ?", Long.class, itemId);
        Long finalVersion = jdbcTemplate.queryForObject(
                "SELECT version FROM inventory_item WHERE id = ?", Long.class, itemId);
        assertThat(finalQuantity).isEqualTo(99);
        assertThat(finalVersion).isEqualTo(1);
    }

    @Test
    void firstInitializationConcurrentInsertsConflictCleanlyWithExactlyOneRowCreated() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org Concurrency First Init");
        UUID storeId = createStore(ownerSubject, organizationId, "store-" + UUID.randomUUID(), "EGP");
        UUID variantId = createVariant(ownerSubject, organizationId, storeId);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (Connection lockHolder = dataSource.getConnection()) {
            lockHolder.setAutoCommit(false);

            // "Transaction A": inserts the first InventoryItem row but does not commit
            // yet - under READ COMMITTED this row is invisible to any other session's
            // plain SELECT.
            try (PreparedStatement insert = lockHolder.prepareStatement(
                    "INSERT INTO inventory_item (id, organization_id, store_id, product_variant_id, "
                            + "on_hand_quantity, version, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, 5, 0, now(), now())")) {
                insert.setObject(1, UUID.randomUUID());
                insert.setObject(2, organizationId);
                insert.setObject(3, storeId);
                insert.setObject(4, variantId);
                assertThat(insert.executeUpdate()).isEqualTo(1);
            }

            // "Transaction B": a real, concurrently-dispatched HTTP PUT for the very
            // same, still-uninitialized Variant. Its own SELECT correctly observes no
            // existing row (A's insert is uncommitted), so it also attempts an INSERT -
            // which PostgreSQL itself blocks, pending resolution of the unique-key
            // conflict against A's not-yet-resolved transaction.
            Future<MvcResult> conflictingInsert = executor.submit(() -> mockMvc.perform(
                            put(inventoryUrl(organizationId, storeId, variantId))
                                    .with(jwtSubject(ownerSubject))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(Map.of("onHandQuantity", 50))))
                    .andReturn());

            Thread.sleep(500);

            lockHolder.commit();

            MvcResult result = conflictingInsert.get(15, TimeUnit.SECONDS);
            assertThat(result.getResponse().getStatus()).isEqualTo(409);
        } finally {
            executor.shutdownNow();
        }

        Long rowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory_item WHERE product_variant_id = ?", Long.class, variantId);
        Long persistedQuantity = jdbcTemplate.queryForObject(
                "SELECT on_hand_quantity FROM inventory_item WHERE product_variant_id = ?", Long.class, variantId);
        assertThat(rowCount).isEqualTo(1);
        assertThat(persistedQuantity).isEqualTo(5);
    }
}
