package com.shoplab.api.testsupport;

import com.shoplab.api.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * El reset deja exactamente la semilla de §13 (design D2). Corre SIN el perfil testing:
 * el servicio se crea con new, igual que en support/SeedData.
 */
class TestDataResetIT extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ApplicationContext context;

    void dirtyTheDatabase() {
        // Un usuario nuevo, la última unidad del producto 5 vendida y una orden nueva
        jdbc.update("""
                INSERT INTO users (email, password_hash, first_name, last_name, birth_date, country, plan,
                                   experience_level, newsletter, terms_accepted_at, status, created_at)
                VALUES ('reset@registration.it', '$2a$10$xxxxxxxxxxxxxxxxxxxxxuxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx',
                        'Ana', 'Gomez', '1990-05-20', 'CO', 'BASIC', 3, false, now(), 'ACTIVE', now())
                """);
        jdbc.update("UPDATE products SET stock = 0 WHERE id = 5");
        Long orderId = jdbc.queryForObject("""
                INSERT INTO orders (user_id, status, payment_method, total, shipping_address, shipping_city,
                                    shipping_postal_code, shipping_country, created_at)
                VALUES (1, 'CONFIRMED', 'CARD', 39.50, 'Calle 10 #20-30', 'Bogotá', '110111', 'CO', now()) RETURNING id
                """, Long.class);
        jdbc.update("INSERT INTO order_items (order_id, product_id, quantity, unit_price) VALUES (?, 5, 1, 39.50)", orderId);
    }

    @Test
    void resetRestoresExactlyTheSeed() {
        dirtyTheDatabase();

        new TestDataResetService(jdbc).reset();

        assertThat(jdbc.queryForList("SELECT id, email, status FROM users ORDER BY id"))
                .extracting(u -> u.get("id"), u -> u.get("email"), u -> u.get("status"))
                .containsExactly(
                        tuple(1L, "standard@shoplab.dev", "ACTIVE"),
                        tuple(2L, "locked@shoplab.dev", "LOCKED"),
                        tuple(3L, "buyer@shoplab.dev", "ACTIVE"));
        List<Map<String, Object>> products = jdbc.queryForList("SELECT id, stock FROM products ORDER BY id");
        assertThat(products).extracting(p -> p.get("id"), p -> p.get("stock"))
                .containsExactly(tuple(1L, 10), tuple(2L, 15), tuple(3L, 0), tuple(4L, 20), tuple(5L, 1), tuple(6L, 8));
        assertThat(jdbc.queryForList("SELECT id, user_id, total FROM orders ORDER BY id"))
                .extracting(o -> o.get("id"), o -> o.get("user_id"), o -> o.get("total"))
                .containsExactly(tuple(1L, 3L, new BigDecimal("109.99")), tuple(2L, 3L, new BigDecimal("45.00")));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_items", Integer.class)).isEqualTo(3);
    }

    @Test
    void idsStartAgainAfterTheSeed() {
        dirtyTheDatabase();

        new TestDataResetService(jdbc).reset();

        // RESTART IDENTITY: el primer usuario después del reset es el 4, como tras un arranque limpio
        Long newUserId = jdbc.queryForObject("""
                INSERT INTO users (email, password_hash, first_name, last_name, birth_date, country, plan,
                                   experience_level, newsletter, terms_accepted_at, status, created_at)
                VALUES ('after@registration.it', '$2a$10$xxxxxxxxxxxxxxxxxxxxxuxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx',
                        'Ana', 'Gomez', '1990-05-20', 'CO', 'BASIC', 3, false, now(), 'ACTIVE', now()) RETURNING id
                """, Long.class);
        assertThat(newUserId).isEqualTo(4L);

        new TestDataResetService(jdbc).reset();   // deja la base limpia para el resto de tests
    }

    @Test
    void withoutTestingProfileThereIsNoResetBean() {
        // design D2: fuera del perfil testing no existe ningún bean capaz de vaciar la base de datos
        assertThat(context.getBeanNamesForType(TestDataResetService.class)).isEmpty();
    }
}
