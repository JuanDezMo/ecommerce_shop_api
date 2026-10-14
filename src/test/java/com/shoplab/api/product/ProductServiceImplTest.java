package com.shoplab.api.product;

import com.shoplab.api.common.exception.BusinessException;
import com.shoplab.api.common.exception.ErrorCode;
import com.shoplab.api.product.dto.ProductResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Reglas del catálogo con el repository mockeado y el mapper real (bootstrap D1).
 */
class ProductServiceImplTest {

    private ProductRepository productRepository;
    private ProductService productService;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        productService = new ProductServiceImpl(productRepository, new ProductMapper());
    }

    static Product product(long id, String sku) {
        Product product = new Product(sku, "Product " + id, "Description " + id,
                new BigDecimal("10.00"), 5, "key" + id, Instant.parse("2026-08-01T08:00:00Z"));
        ReflectionTestUtils.setField(product, "id", id);
        return product;
    }

    @Test
    void findAllKeepsTheRepositoryOrder() {
        when(productRepository.findAllByOrderByIdAsc())
                .thenReturn(List.of(product(1, "SKU-1"), product(2, "SKU-2"), product(3, "SKU-3")));

        List<ProductResponse> products = productService.findAll();

        assertThat(products).extracting(ProductResponse::id).containsExactly(1L, 2L, 3L);
        assertThat(products).extracting(ProductResponse::sku).containsExactly("SKU-1", "SKU-2", "SKU-3");
    }

    @Test
    void findAllReturnsEmptyListWhenThereAreNoProducts() {
        when(productRepository.findAllByOrderByIdAsc()).thenReturn(List.of());

        assertThat(productService.findAll()).isEmpty();
    }

    @Test
    void findByIdReturnsTheProduct() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product(1, "SL-HEAD-001")));

        ProductResponse response = productService.findById(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.sku()).isEqualTo("SL-HEAD-001");
    }

    @Test
    void findByIdThrowsProductNotFoundWithTheId() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.findById(99L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Product 99 not found")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
    }
}
