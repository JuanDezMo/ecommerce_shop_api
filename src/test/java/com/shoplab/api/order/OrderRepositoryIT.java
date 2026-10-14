package com.shoplab.api.order;

import com.shoplab.api.product.ProductRepository;
import com.shoplab.api.support.IntegrationTest;
import com.shoplab.api.support.SeedData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Mapeo de Order / OrderItem contra V3 y consultas con @EntityGraph (design D2 y D5).
 * Las lecturas de relaciones lazy se hacen dentro de una transacción, como en OrderServiceImpl.
 */
class OrderRepositoryIT extends IntegrationTest {

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    ProductRepository productRepository;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate transaction;

    @AfterEach
    void restoreSeed() {
        SeedData.restoreOrdersAndStock(jdbc);
    }

    @Test
    void buyerOrdersComeNewestFirstWithItemsAndProducts() {
        transaction.executeWithoutResult(status -> {
            List<Order> orders = orderRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(3L);

            assertThat(orders).extracting(Order::getId).containsExactly(2L, 1L);
            Order first = orders.get(1);
            assertThat(first.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            assertThat(first.getPaymentMethod()).isEqualTo(PaymentMethod.CARD);
            assertThat(first.getTotal()).isEqualTo(new BigDecimal("109.99"));
            assertThat(first.getShippingCity()).isEqualTo("Bogotá");
            // @OrderBy("id"): los ítems salen en el orden en que se insertaron
            assertThat(first.getItems())
                    .extracting(item -> item.getProduct().getSku(), OrderItem::getQuantity, OrderItem::getUnitPrice)
                    .containsExactly(
                            tuple("SL-HEAD-001", 1, new BigDecimal("59.99")),
                            tuple("SL-MOUS-004", 2, new BigDecimal("25.00")));
        });
    }

    @Test
    void findByIdAndUserIdOnlyFindsOwnOrders() {
        assertThat(orderRepository.findByIdAndUserId(1L, 3L)).isPresent();
        // La orden 1 es de buyer (3): para standard (1) "no existe" (§7)
        assertThat(orderRepository.findByIdAndUserId(1L, 1L)).isEmpty();
    }

    @Test
    void savingAnOrderCascadesItsItems() {
        Long orderId = transaction.execute(status -> {
            Order order = new Order();
            order.setUserId(1L);
            order.setStatus(OrderStatus.CONFIRMED);
            order.setPaymentMethod(PaymentMethod.TRANSFER);
            order.setTotal(new BigDecimal("84.99"));
            order.setShippingAddress("Calle 10 #20-30");
            order.setShippingCity("Bogotá");
            order.setShippingPostalCode("110111");
            order.setShippingCountry("CO");
            order.setCreatedAt(Instant.parse("2026-10-11T12:00:00Z"));
            order.addItem(new OrderItem(productRepository.getReferenceById(1L), 1, new BigDecimal("59.99")));
            order.addItem(new OrderItem(productRepository.getReferenceById(4L), 1, new BigDecimal("25.00")));
            return orderRepository.save(order).getId();
        });

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT order_id, product_id, quantity FROM order_items WHERE order_id = ? ORDER BY id", orderId);
        assertThat(rows).extracting(r -> r.get("order_id"), r -> r.get("product_id"), r -> r.get("quantity"))
                .containsExactly(tuple(orderId, 1L, 1), tuple(orderId, 4L, 1));
    }
}
