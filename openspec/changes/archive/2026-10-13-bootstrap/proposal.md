# Proposal

## Why

El repositorio solo tiene documentación: no existe proyecto Gradle, base de datos ni contenedores. Antes de construir cualquier feature (catálogo, registro, login, órdenes) hace falta una base común que fije **cómo se comunican las capas**, **cómo responde la API cuando algo falla** y **con qué datos arranca**, para que cada change posterior solo agregue su feature sin redecidir la arquitectura.

## What Changes

- Proyecto Gradle (Kotlin DSL, con wrapper) con Java 21 y Spring Boot 3.x (Web, Data JPA, Security, Validation, Actuator), `jjwt` 0.12.x declarado en `build.gradle.kts`, springdoc-openapi y paquete raíz `com.shoplab.api`. Tareas `test` (sin Docker) e `integrationTest` (Testcontainers).
- Estructura de paquetes por feature (`auth`, `user`, `product`, `order`, `security`, `config`, `common`, `testsupport`) con las reglas de dependencia entre capas **verificadas por un test de arquitectura** (ArchUnit).
- Módulo `common`: `ErrorResponse`, `FieldErrorResponse`, `ErrorCode` (con `HttpStatus` y mensaje, catálogo completo de §9.1), `BusinessException`, `ValidationPatterns` y `GlobalExceptionHandler`.
- Jackson estricto (sin coerción de escalares ni decimales a enteros) y `Clock` en UTC como bean.
- Configuración JPA de §11.1 (`open-in-view: false`, `ddl-auto: validate`, zona horaria UTC).
- Migraciones Flyway `V1`–`V4`: tablas `users`, `products`, `orders`, `order_items` con sus restricciones y longitudes (§6.6), y datos semilla (3 usuarios, 6 productos, historial de órdenes de `buyer`).
- `SecurityConfig` **provisional**: todo público, stateless, sin CSRF, y bean `PasswordEncoder` (BCrypt). Lo reemplaza el change `auth-jwt-filter`.
- Actuator con solo `/actuator/health` y Swagger UI en `/swagger-ui.html`.
- Perfiles `dev`, `testing` y `default` (solo configuración; las utilidades de `testing` llegan en el change `testing-profile`).
- `Dockerfile` multi-stage, `docker-compose.yml`, `.env.example` y `.gitignore`.
- Base de pruebas: JUnit 5, Mockito, MockMvc, Testcontainers (PostgreSQL 16) y ArchUnit.

## Capabilities

### New Capabilities

- `error-handling`: formato único de las respuestas de error (`ErrorResponse`), catálogo de `ErrorCode`, traducción de excepciones (validación, tipos, JSON mal formado, ruta inexistente, método no soportado, media type, negocio, error inesperado) y orden determinista de `fieldErrors`.
- `service-health`: endpoint público `/actuator/health` que indica si la API y su base de datos están disponibles, sin exponer otros endpoints de Actuator.
- `seed-data`: datos iniciales que existen en todos los perfiles tras arrancar (usuarios de prueba, catálogo de 6 productos con casos de stock y órdenes previas de `buyer`).

### Modified Capabilities

<!-- Ninguna: el proyecto aún no tiene specs. -->

## Impact

- **Código nuevo**: todo el esqueleto en `src/main/java/com/shoplab/api` y `src/test/java/com/shoplab/api`; ninguna feature de negocio todavía.
- **API**: aún no hay endpoints de negocio. Solo existen `/actuator/health`, Swagger y las respuestas de error (por ejemplo, 404 `NOT_FOUND` para rutas inexistentes).
- **Base de datos**: crea el esquema completo y la semilla. Los changes siguientes no agregan migraciones salvo que cambie el modelo.
- **Dependencias**: Spring Boot 3.x, PostgreSQL driver, Flyway, jjwt 0.12.x, springdoc-openapi, Testcontainers, ArchUnit.
- **Infraestructura**: Docker y Docker Compose con variables `DB_PASSWORD`, `JWT_SECRET` y `SPRING_PROFILES_ACTIVE`.
- **Documentación**: `docs/shop-backend-arquitectura.md` sigue siendo la fuente de verdad. Este change agrega la tabla de productos semilla y la prueba de arquitectura (ver `design.md`).
