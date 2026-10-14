package com.shoplab.api.product;

import com.shoplab.api.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mapeo de Product contra la tabla products de V2 y los datos semilla de V4 (design D2 y D3).
 */
class ProductRepositoryIT extends IntegrationTest {

    @Autowired
    ProductRepository productRepository;

    @Test
    void findAllByOrderByIdAscReturnsTheSixSeedProductsInOrder() {
        List<Product> products = productRepository.findAllByOrderByIdAsc();

        assertThat(products).extracting(Product::getId).containsExactly(1L, 2L, 3L, 4L, 5L, 6L);
    }

    @Test
    void findByIdMapsEveryColumn() {
        Product product = productRepository.findById(1L).orElseThrow();

        assertThat(product.getSku()).isEqualTo("SL-HEAD-001");
        assertThat(product.getName()).isEqualTo("Wireless Headphones");
        assertThat(product.getDescription()).isEqualTo("Over-ear bluetooth headphones");
        assertThat(product.getPrice()).isEqualTo(new BigDecimal("59.99"));
        assertThat(product.getStock()).isEqualTo(10);
        assertThat(product.getImageKey()).isEqualTo("headphones");
        assertThat(product.getCreatedAt()).isNotNull();
    }

    @Test
    void priceKeepsTwoDecimals() {
        Product product = productRepository.findById(4L).orElseThrow();

        // 25.00 y no 25: la escala de numeric(12,2) llega intacta al BigDecimal
        assertThat(product.getPrice()).isEqualTo(new BigDecimal("25.00"));
    }
}
