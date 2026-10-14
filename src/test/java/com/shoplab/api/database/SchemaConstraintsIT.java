package com.shoplab.api.database;

import com.shoplab.api.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La base de datos es la última línea de defensa: rechaza datos inválidos aunque el código falle.
 * @Transactional deshace lo insertado al terminar cada test.
 */
@Transactional
class SchemaConstraintsIT extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void rejectsNegativeStock() {
        assertThatThrownBy(() -> insertProduct("SKU-NEG-STOCK", "10.00", -1))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsNegativePrice() {
        assertThatThrownBy(() -> insertProduct("SKU-NEG-PRICE", "-0.01", 5))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsDuplicateSku() {
        insertProduct("SKU-DUP", "10.00", 5);

        assertThatThrownBy(() -> insertProduct("SKU-DUP", "10.00", 5))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsDuplicateEmail() {
        insertUser("dup@shoplab.dev", 3);

        assertThatThrownBy(() -> insertUser("dup@shoplab.dev", 3))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsExperienceLevelOutOfRange() {
        assertThatThrownBy(() -> insertUser("level@shoplab.dev", 6))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsOrderItemWithZeroQuantity() {
        long userId = insertUser("items@shoplab.dev", 3);
        long productId = insertProduct("SKU-ITEM", "10.00", 5);
        long orderId = insertOrder(userId);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO order_items (order_id, product_id, quantity, unit_price) VALUES (?, ?, 0, 10.00)",
                orderId, productId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsOrderForUnknownUser() {
        assertThatThrownBy(() -> insertOrder(999_999L))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private long insertProduct(String sku, String price, int stock) {
        return jdbc.queryForObject("""
                INSERT INTO products (sku, name, description, price, stock, image_key, created_at)
                VALUES (?, 'Test product', 'Test', CAST(? AS numeric), ?, 'test', now())
                RETURNING id
                """, Long.class, sku, price, stock);
    }

    private long insertUser(String email, int experienceLevel) {
        return jdbc.queryForObject("""
                INSERT INTO users (email, password_hash, first_name, last_name, phone, birth_date, country, plan,
                                   experience_level, newsletter, terms_accepted_at, status, created_at)
                VALUES (?, 'hash', 'Test', 'User', NULL, DATE '1990-01-01', 'CO', 'BASIC', ?, false, now(), 'ACTIVE', now())
                RETURNING id
                """, Long.class, email, experienceLevel);
    }

    private long insertOrder(long userId) {
        return jdbc.queryForObject("""
                INSERT INTO orders (user_id, status, payment_method, total, shipping_address, shipping_city,
                                    shipping_postal_code, shipping_country, created_at)
                VALUES (?, 'CONFIRMED', 'CARD', 10.00, 'Calle 10 #20-30', 'Bogotá', '110111', 'CO', now())
                RETURNING id
                """, Long.class, userId);
    }
}
