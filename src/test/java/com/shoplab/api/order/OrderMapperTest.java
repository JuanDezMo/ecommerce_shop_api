package com.shoplab.api.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.shoplab.api.order.dto.OrderItemResponse;
import com.shoplab.api.order.dto.OrderResponse;
import com.shoplab.api.order.dto.ShippingResponse;
import com.shoplab.api.product.Product;
import com.shoplab.api.support.ContractAssertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class OrderMapperTest {

    static final Instant NOW = Instant.parse("2026-10-11T15:04:05Z");

    private final OrderMapper mapper = new OrderMapper();

    static Product product(long id, String sku, String name, String price, String imageKey) {
        Product product = new Product(sku, name, "desc", new BigDecimal(price), 10, imageKey, NOW);
        ReflectionTestUtils.setField(product, "id", id);
        return product;
    }

    /** Orden de prueba: 2 × producto 1 (59.99) + 1 × producto 4 (25.00) = 144.98. */
    static Order sampleOrder() {
        Order order = new Order();
        ReflectionTestUtils.setField(order, "id", 3L);
        order.setUserId(1L);
        order.setStatus(OrderStatus.CONFIRMED);
        order.setPaymentMethod(PaymentMethod.CARD);
        order.setTotal(new BigDecimal("144.98"));
        order.setShippingAddress("Calle 10 #20-30");
        order.setShippingCity("Bogotá");
        order.setShippingPostalCode("110111");
        order.setShippingCountry("CO");
        order.setCreatedAt(NOW);
        order.addItem(new OrderItem(product(1, "SL-HEAD-001", "Wireless Headphones", "59.99", "headphones"), 2, new BigDecimal("59.99")));
        order.addItem(new OrderItem(product(4, "SL-MOUS-004", "Gaming Mouse", "25.00", "mouse"), 1, new BigDecimal("25.00")));
        return order;
    }

    @Test
    void mapsOrderItemsAndShipping() {
        OrderResponse response = mapper.toResponse(sampleOrder());

        assertThat(response.id()).isEqualTo(3L);
        assertThat(response.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(response.paymentMethod()).isEqualTo(PaymentMethod.CARD);
        assertThat(response.total()).isEqualTo(new BigDecimal("144.98"));
        assertThat(response.createdAt()).isEqualTo(NOW);
        assertThat(response.shipping()).isEqualTo(new ShippingResponse("Calle 10 #20-30", "Bogotá", "110111", "CO"));
        assertThat(response.items())
                .extracting(OrderItemResponse::productId, OrderItemResponse::sku, OrderItemResponse::name,
                        OrderItemResponse::imageKey, OrderItemResponse::quantity, OrderItemResponse::subtotal)
                .containsExactly(
                        tuple(1L, "SL-HEAD-001", "Wireless Headphones", "headphones", 2, new BigDecimal("119.98")),
                        tuple(4L, "SL-MOUS-004", "Gaming Mouse", "mouse", 1, new BigDecimal("25.00")));
    }

    @Test
    void serializedResponseMatchesTheContractWithoutUserId() throws Exception {
        ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)   // igual que Spring Boot
                .build();

        String json = objectMapper.writeValueAsString(mapper.toResponse(sampleOrder()));

        ContractAssertions.assertMatchesSchema(json, "order-response");
        assertThat(json)
                .contains("\"subtotal\":25.00")
                .contains("\"total\":144.98")
                .doesNotContain("userId");
    }
}
