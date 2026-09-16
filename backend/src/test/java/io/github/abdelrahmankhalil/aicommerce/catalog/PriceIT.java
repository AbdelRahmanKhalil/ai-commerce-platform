package io.github.abdelrahmankhalil.aicommerce.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** ProductVariant price validation (ADR 006): BigDecimal, never floating-point semantics. */
class PriceIT extends AbstractCatalogIntegrationTest {

    @Test
    void priceRoundTripsExactly() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org Price Roundtrip");
        UUID storeId = createStore(ownerSubject, organizationId, "store-" + UUID.randomUUID(), "EGP");

        Map<String, Object> body = new HashMap<>(simpleProductBody("Priced", "priced"));
        body.put("variants", List.of(variant("PRICE-1", "ACTIVE", new BigDecimal("9999999999.99"), Map.of())));

        String json = mockMvc.perform(post(productsUrl(organizationId, storeId))
                        .with(jwtSubject(ownerSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID productId = UUID.fromString(objectMapper.readTree(json).get("productId").asText());

        String detailJson = mockMvc.perform(get(productsUrl(organizationId, storeId) + "/{productId}", productId)
                        .with(jwtSubject(ownerSubject)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        BigDecimal price = new BigDecimal(objectMapper.readTree(detailJson).get("variants").get(0).get("price").asText());
        assertThat(price).isEqualByComparingTo("9999999999.99");
    }

    @Test
    void negativePriceIsRejected() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org Price Negative");
        UUID storeId = createStore(ownerSubject, organizationId, "store-" + UUID.randomUUID(), "EGP");

        Map<String, Object> body = new HashMap<>(simpleProductBody("Negative", "negative"));
        body.put("variants", List.of(variant("NEG-1", "ACTIVE", new BigDecimal("-1.00"), Map.of())));

        mockMvc.perform(post(productsUrl(organizationId, storeId))
                        .with(jwtSubject(ownerSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void moreThanTwoFractionDigitsIsRejected() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org Price Fraction");
        UUID storeId = createStore(ownerSubject, organizationId, "store-" + UUID.randomUUID(), "EGP");

        Map<String, Object> body = new HashMap<>(simpleProductBody("Fraction", "fraction"));
        body.put("variants", List.of(variant("FRAC-1", "ACTIVE", new BigDecimal("10.123"), Map.of())));

        mockMvc.perform(post(productsUrl(organizationId, storeId))
                        .with(jwtSubject(ownerSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void exceedingSupportedIntegerPrecisionIsRejected() throws Exception {
        String ownerSubject = provisionedSubject();
        UUID organizationId = createOrganization(ownerSubject, "Org Price Overflow");
        UUID storeId = createStore(ownerSubject, organizationId, "store-" + UUID.randomUUID(), "EGP");

        Map<String, Object> body = new HashMap<>(simpleProductBody("Overflow", "overflow"));
        body.put("variants", List.of(variant("OVF-1", "ACTIVE", new BigDecimal("99999999999.99"), Map.of())));

        mockMvc.perform(post(productsUrl(organizationId, storeId))
                        .with(jwtSubject(ownerSubject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }
}
