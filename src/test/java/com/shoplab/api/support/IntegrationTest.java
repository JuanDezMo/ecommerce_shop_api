package com.shoplab.api.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base de los tests de integración (*IT): levanta la aplicación completa contra un PostgreSQL 16 real.
 * Recibe el secreto JWT de prueba (TestJwt): sin secreto la aplicación no arranca.
 * El contenedor es estático y se comparte entre todas las clases: arranca una sola vez por ejecución.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = TestJwt.SECRET_PROPERTY)
public abstract class IntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    static {
        POSTGRES.start();
    }
}
