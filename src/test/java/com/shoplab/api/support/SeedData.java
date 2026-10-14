package com.shoplab.api.support;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Devuelve la base de datos compartida de los *IT a los valores semilla de §13 (design D8 del change orders).
 * Se llama en @AfterEach de los tests que crean órdenes o descuentan stock, porque ProductCatalogIT y SeedDataIT
 * dependen de esos valores. Lo reemplazará POST /test/reset (change testing-profile).
 */
public final class SeedData {

    /** Las órdenes semilla son las 1 y 2 (de buyer): todo lo demás lo crearon los tests. */
    private static final long LAST_SEED_ORDER_ID = 2;

    private SeedData() {
    }

    public static void restoreOrdersAndStock(JdbcTemplate jdbc) {
        // Primero los ítems y las órdenes (claves foráneas), luego los usuarios de prueba
        jdbc.update("DELETE FROM order_items WHERE order_id > ?", LAST_SEED_ORDER_ID);
        jdbc.update("DELETE FROM orders WHERE id > ?", LAST_SEED_ORDER_ID);
        jdbc.update("DELETE FROM users WHERE email LIKE '%@registration.it'");
        // Stock de la tabla de productos semilla (§13)
        jdbc.update("""
                UPDATE products SET stock = CASE id
                    WHEN 1 THEN 10 WHEN 2 THEN 15 WHEN 3 THEN 0
                    WHEN 4 THEN 20 WHEN 5 THEN 1  WHEN 6 THEN 8 END
                WHERE id BETWEEN 1 AND 6
                """);
    }
}
