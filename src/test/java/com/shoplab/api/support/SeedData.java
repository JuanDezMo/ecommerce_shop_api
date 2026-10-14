package com.shoplab.api.support;

import com.shoplab.api.testsupport.TestDataResetService;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Devuelve la base de datos compartida de los *IT a la semilla de §13.
 * Se llama en @AfterEach de los tests que crean órdenes o descuentan stock, porque ProductCatalogIT y SeedDataIT
 * dependen de esos valores.
 * Usa la misma lógica que POST /api/v1/test/reset (design D5 de testing-profile). Se crea con new porque estos
 * tests corren sin el perfil testing y el bean no existe.
 */
public final class SeedData {

    private SeedData() {
    }

    public static void restoreOrdersAndStock(JdbcTemplate jdbc) {
        new TestDataResetService(jdbc).reset();
    }
}
