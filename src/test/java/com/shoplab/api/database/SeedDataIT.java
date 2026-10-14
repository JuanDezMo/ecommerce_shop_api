package com.shoplab.api.database;

import com.shoplab.api.support.IntegrationTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Datos semilla de V4 (specs/seed-data y tablas de design D7 / §13).
 */
class SeedDataIT extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    Flyway flyway;

    @Test
    void seedUsersHaveFixedIdsAndStatuses() {
        List<Map<String, Object>> users = jdbc.queryForList("SELECT id, email, status FROM users ORDER BY id");

        assertThat(users).extracting(u -> u.get("id"), u -> u.get("email"), u -> u.get("status"))
                .containsExactly(
                        tuple(1L, "standard@shoplab.dev", "ACTIVE"),
                        tuple(2L, "locked@shoplab.dev", "LOCKED"),
                        tuple(3L, "buyer@shoplab.dev", "ACTIVE"));
    }

    @Test
    void seedPasswordsAreBcryptHashesOfSecret123() {
        List<String> hashes = jdbc.queryForList("SELECT password_hash FROM users", String.class);

        assertThat(hashes).hasSize(3).allSatisfy(hash -> {
            assertThat(hash).isNotEqualTo("Secret_123").startsWith("$2a$10$");
            assertThat(passwordEncoder.matches("Secret_123", hash)).isTrue();
        });
    }

    @Test
    void seedProductsMatchTheCatalogTable() {
        List<Map<String, Object>> products = jdbc.queryForList(
                "SELECT id, sku, price, stock, image_key FROM products ORDER BY id");

        assertThat(products)
                .extracting(p -> p.get("id"), p -> p.get("sku"), p -> p.get("price"), p -> p.get("stock"), p -> p.get("image_key"))
                .containsExactly(
                        tuple(1L, "SL-HEAD-001", new BigDecimal("59.99"), 10, "headphones"),
                        tuple(2L, "SL-KEYB-002", new BigDecimal("89.90"), 15, "keyboard"),
                        tuple(3L, "SL-WATC-003", new BigDecimal("149.00"), 0, "smartwatch"),
                        tuple(4L, "SL-MOUS-004", new BigDecimal("25.00"), 20, "mouse"),
                        tuple(5L, "SL-SPKR-005", new BigDecimal("39.50"), 1, "speaker"),
                        tuple(6L, "SL-BACK-006", new BigDecimal("45.00"), 8, "backpack"));
    }

    @Test
    void buyerHasTwoConfirmedOrdersWhoseTotalsMatchTheirItems() {
        List<Map<String, Object>> orders = jdbc.queryForList("""
                SELECT o.id, o.user_id, o.status, o.payment_method, o.total,
                       (SELECT sum(i.unit_price * i.quantity) FROM order_items i WHERE i.order_id = o.id) AS items_total
                FROM orders o ORDER BY o.id
                """);

        assertThat(orders)
                .extracting(o -> o.get("id"), o -> o.get("user_id"), o -> o.get("status"), o -> o.get("payment_method"), o -> o.get("total"))
                .containsExactly(
                        tuple(1L, 3L, "CONFIRMED", "CARD", new BigDecimal("109.99")),
                        tuple(2L, 3L, "CONFIRMED", "CASH", new BigDecimal("45.00")));
        assertThat(orders).allSatisfy(o ->
                assertThat((BigDecimal) o.get("items_total")).isEqualByComparingTo((BigDecimal) o.get("total")));
    }

    @Test
    void seedOrderItemsUseTheProductPrice() {
        Integer mismatches = jdbc.queryForObject("""
                SELECT count(*) FROM order_items i JOIN products p ON p.id = i.product_id
                WHERE i.unit_price <> p.price
                """, Integer.class);

        assertThat(mismatches).isZero();
    }

    @Test
    void standardUserHasNoOrders() {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM orders WHERE user_id = 1", Integer.class);

        assertThat(count).isZero();
    }

    @Test
    @Transactional
    void identitySequenceContinuesAfterTheSeed() {
        // Si V4 escribiera los ids a mano, la secuencia seguiría en 1 y este INSERT chocaría con un producto semilla
        Long newId = jdbc.queryForObject("""
                INSERT INTO products (sku, name, description, price, stock, image_key, created_at)
                VALUES ('SL-NEW-999', 'New', 'New', 1.00, 1, 'new', now()) RETURNING id
                """, Long.class);

        assertThat(newId).isGreaterThan(6L);
    }

    @Test
    void migratingAgainDoesNotDuplicateTheSeed() {
        int executed = flyway.migrate().migrationsExecuted;

        assertThat(executed).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM products", Integer.class)).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_items", Integer.class)).isEqualTo(3);
    }
}
