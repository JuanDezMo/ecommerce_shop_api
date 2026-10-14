# Tasks

> Convención (bootstrap D10): `*Test` corre con `./gradlew test` (sin Docker) y `*IT` con `./gradlew integrationTest` (Testcontainers sobre colima). Cada tarea sigue el ciclo test que falla → código mínimo → refactor. Gradle se ejecuta con Java 21 (`JAVA_HOME=$(/usr/libexec/java_home -v 21)`).

## 1. Reset de datos

- [x] 1.1 Escribir `TestDataResetIT` (extiende `IntegrationTest`): registrar un usuario `@registration.it`, crear una orden que compre la última unidad del producto 5 y luego llamar a `new TestDataResetService(jdbc).reset()`; verificar que hay exactamente 3 usuarios, 6 productos con el stock de §13, 2 órdenes y 3 ítems (mismas consultas que `SeedDataIT`), y que el siguiente `INSERT` de usuario recibe id 4. Comprobar también que, sin el perfil `testing`, el contexto no tiene ningún bean `TestDataResetService` (`ApplicationContext.getBeanNamesForType`). Verlo fallar. Crear `testsupport/TestDataResetService` con `@Service @Profile("testing")` (design D2). Verificar con `./gradlew integrationTest --tests TestDataResetIT`.
- [x] 1.2 Cambiar `support/SeedData.restoreOrdersAndStock` para que delegue en `TestDataResetService` (design D5) y borrar los valores de stock escritos a mano. Verificar que `ProductRepositoryIT`, `OrderRepositoryIT`, `OrdersIT`, `ProductCatalogIT` y `SeedDataIT` pasan en la misma ejecución.

## 2. Token expirado bajo demanda

- [x] 2.1 Crear `docs/contracts/expired-token-response.schema.json` (design D3) y ampliar `ContractAssertionsTest`: `{"accessToken":"a.b.c","tokenType":"Bearer"}` cumple el contrato, y con `expiresIn` o con `tokenType` `Basic` falla. Verificar con `./gradlew test --tests ContractAssertionsTest`.
- [x] 2.2 Escribir `ExpiredTokenFactoryTest` (Mockito para `UserRepository`, `JwtProperties` con `TestJwt.SECRET` y TTL 15 min, reloj real): para un usuario existente, el token devuelto se rechaza con `AUTH_TOKEN_EXPIRED` al pasarlo a un `JjwtService` real con el mismo secreto, y su `sub` y `email` son los del usuario (verificado con `Jwts.parser().clockSkewSeconds(...)`); un email inexistente → `BusinessException` `USER_NOT_FOUND`. Verlo fallar. Crear `testsupport/dto/ExpiredTokenRequest`, `ExpiredTokenResponse` y `testsupport/ExpiredTokenFactory`. Verificar con `./gradlew test --tests ExpiredTokenFactoryTest`.

## 3. Cabeceras de caos

- [x] 3.1 Escribir `ChaosFilterTest` con los casos de design D6, usando una subclase de prueba que sobrescribe `sleep` para registrar los milisegundos sin esperar, y validando el body del error forzado con `error-response`. Verlo fallar. Hacer `public` `security/ErrorResponseWriter` y añadir la sobrecarga con status explícito; crear `testsupport/ChaosFilter` (design D4). Verificar con `./gradlew test --tests ChaosFilterTest` y que `./gradlew test` sigue pasando completo.

## 4. Endpoints del perfil testing

- [x] 4.1 Escribir `TestSupportIT` (extiende `IntegrationTest`, `@ActiveProfiles("testing")`, reset con `SeedData` en `@AfterEach`) con los escenarios de *Restaurar los datos semilla* y *Token expirado bajo demanda* de la spec: reset tras registrar y comprar → 204 sin body y semilla restaurada; usuario registrado después tiene id 4; dos resets seguidos → 204; `GET /test/reset` → 405; token expirado de `standard` cumple `expired-token-response` y `/users/me` con él → 401 `AUTH_TOKEN_EXPIRED`; email con mayúsculas → 200; `nobody@shoplab.dev` → 404 `USER_NOT_FOUND`; `{}` y email en blanco → 400. Verlo fallar. Crear `testsupport/TestSupportController` (`@Profile("testing")`). Verificar con `./gradlew integrationTest --tests TestSupportIT`.
- [x] 4.2 Añadir a `TestSupportIT` los escenarios de *Retraso*, *Error forzado* y *Cabeceras antes que la seguridad*: `X-Delay: 500` en `/products` tarda ≥ 500 ms y responde 200; `X-Delay: abc` no retrasa; `X-Force-Error: 500` en `/products` → 500 `FORCED_ERROR` con contrato y `path`; `X-Force-Error: 503` en `POST /orders` con token → 503 sin crear la orden ni descontar stock; `200`, `abc` y `700` se ignoran; `/users/me` sin token con `X-Force-Error: 418` → 418; `X-Delay: 300` + `X-Force-Error: 502` → 502 tras ≥ 300 ms. Añadir a `TokenAuthenticationIT` que sin el perfil `X-Force-Error: 500` en `/products` responde 200. Verificar que pasan.

## 5. Documentación y cierre

- [x] 5.1 Actualizar `docs/shop-backend-arquitectura.md`: en §3.3, `ExpiredTokenFactory` → `UserRepository` en lugar de `TestSupportController` (design D1); en la tabla de §7.5, `POST /test/tokens/expired` → 200 → `expired-token-response.schema.json`. Ejecutar `./gradlew clean check` y verificar que todos los tests pasan, incluida `ArchitectureTest` con el paquete `testsupport`.

## Workflow follow-up

- Revisión del usuario y commit (los hace el usuario).
- Archivar el change con `/opsx:archive testing-profile` para crear `openspec/specs/testing-support/`. Con esto se completa el plan de entrega de §16.
