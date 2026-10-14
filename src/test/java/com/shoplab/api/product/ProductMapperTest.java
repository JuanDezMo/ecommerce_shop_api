package com.shoplab.api.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoplab.api.product.dto.ProductResponse;
import com.shoplab.api.support.ContractAssertions;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ProductMapperTest {

    private final ProductMapper mapper = new ProductMapper();

    static Product mouse() {
        Product product = new Product("SL-MOUS-004", "Gaming Mouse", "Ergonomic gaming mouse with 6 buttons",
                new BigDecimal("25.00"), 20, "mouse", Instant.parse("2026-08-01T08:00:00Z"));
        // El id lo pone la base de datos; en los tests unitarios se fija a mano (design D2)
        ReflectionTestUtils.setField(product, "id", 4L);
        return product;
    }

    @Test
    void copiesTheSevenFields() {
        ProductResponse response = mapper.toResponse(mouse());

        assertThat(response).isEqualTo(new ProductResponse(4L, "SL-MOUS-004", "Gaming Mouse",
                "Ergonomic gaming mouse with 6 buttons", new BigDecimal("25.00"), 20, "mouse"));
    }

    @Test
    void priceKeepsItsScale() {
        assertThat(mapper.toResponse(mouse()).price().scale()).isEqualTo(2);
    }

    @Test
    void serializedResponseMatchesTheContract() throws Exception {
        String json = new ObjectMapper().writeValueAsString(mapper.toResponse(mouse()));

        ContractAssertions.assertMatchesSchema(json, "product-response");
        assertThat(json).contains("\"price\":25.00").doesNotContain("createdAt");
    }
}
