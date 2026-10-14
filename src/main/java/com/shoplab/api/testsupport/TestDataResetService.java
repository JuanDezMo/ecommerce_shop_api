package com.shoplab.api.testsupport;

import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Statement;

/**
 * Devuelve la base de datos a la semilla de §13 (POST /api/v1/test/reset).
 * Usa el MISMO script que Flyway (V4__seed_data.sql): una sola fuente de verdad para los datos semilla.
 * Solo existe con el perfil testing; los tests del backend lo crean con new (support/SeedData).
 */
@Service
@Profile("testing")
public class TestDataResetService {

    private static final String SEED_SCRIPT = "db/migration/V4__seed_data.sql";

    private final JdbcTemplate jdbc;

    public TestDataResetService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void reset() {
        // TRUNCATE y script en la MISMA conexión: con @Transactional, si el script falla
        // el TRUNCATE también se deshace y la base no queda vacía (design D2)
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (Statement statement = connection.createStatement()) {
                // Las cuatro tablas juntas: PostgreSQL resuelve las claves foráneas entre ellas.
                // RESTART IDENTITY reinicia los ids: el siguiente usuario registrado será el 4
                statement.execute("TRUNCATE order_items, orders, users, products RESTART IDENTITY");
            }
            ScriptUtils.executeSqlScript(connection, new ClassPathResource(SEED_SCRIPT));
            return null;
        });
    }
}
