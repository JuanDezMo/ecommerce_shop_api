# Tasks

> Convención (bootstrap D10): `*Test` corre con `./gradlew test` (sin Docker) y `*IT` con `./gradlew integrationTest` (Testcontainers sobre colima). Cada tarea sigue el ciclo test que falla → código mínimo → refactor. Gradle se ejecuta con Java 21 (`JAVA_HOME=$(/usr/libexec/java_home -v 21)`). Cada `@WebMvcTest` nombra su controller.

## 1. Validación del token

- [x] 1.1 Crear `support/TestTokens` (design D7) con métodos para un token válido, expirado (reloj 2 h en el pasado, TTL 1 h), firmado con otro secreto, con el payload alterado, sin firma (`alg: none`), con `sub` `abc`, sin `email`, y expirado + alterado. Ampliar `JjwtServiceTest` con `parse`: válido → `AuthUser(1, "standard@shoplab.dev")`; `abc.def` → `MALFORMED`; otro secreto, payload alterado, `alg: none`, `sub` no numérico y sin `email` → `INVALID`; expirado → `EXPIRED`; expirado + alterado → `INVALID`. Verlo fallar. Crear `security/AuthUser`, `security/InvalidTokenException`, añadir `parse` a `JwtService` e implementarlo en `JjwtService` (design D2). Verificar con `./gradlew test --tests JjwtServiceTest`.

## 2. Filtro y respuestas JSON de seguridad

- [x] 2.1 Escribir `JwtAuthenticationFilterTest` con los casos de design D8 (sin cabecera, `Basic xyz`, `Bearer ` vacío y solo espacios, `parse` que lanza cada `AUTH_TOKEN_*`, token válido) verificando el `SecurityContextHolder`, el atributo `auth.error` y que siempre se llama a `chain.doFilter`; limpiar el `SecurityContextHolder` en `@AfterEach`. Verlo fallar. Crear `security/JwtAuthenticationFilter` (`OncePerRequestFilter`, sin `@Component`). Verificar con `./gradlew test --tests JwtAuthenticationFilterTest`.
- [x] 2.2 Escribir `JwtAuthenticationEntryPointTest` y `JsonAccessDeniedHandlerTest` (`MockHttpServletRequest` / `MockHttpServletResponse`, `ObjectMapper` de Spring): sin atributo → 401 `AUTH_TOKEN_MISSING`; con atributo `AUTH_TOKEN_EXPIRED` → 401 con ese `code` y su `message`; el handler → 403 `ACCESS_DENIED`; `Content-Type` JSON, `path` de la petición, `fieldErrors` `[]`, y el body cumple `error-response`. Verlos fallar. Crear `security/ErrorResponseWriter`, `JwtAuthenticationEntryPoint` y `JsonAccessDeniedHandler` (design D5). Verificar que pasan.

## 3. Reglas de seguridad definitivas

- [x] 3.1 Crear `support/WebMvcSecurityConfig` (design D7). Escribir `SecurityRulesTest` (`@WebMvcTest(ProductController.class)`, `@Import(WebMvcSecurityConfig.class)`, `@MockitoBean ProductService`): `GET /api/v1/products` sin token → 200; `GET /api/v1/products` con `Bearer abc.def` → 200; `DELETE /api/v1/products/1` sin token → 405; `GET /api/v1/products/1/reviews` sin token → 404; `GET /api/v1/foo` sin token → 401 `AUTH_TOKEN_MISSING`, con `Basic xyz` → `MISSING`, con `Bearer ` → `MALFORMED`, con `abc.def` → `MALFORMED`, con otro secreto → `INVALID`, expirado → `EXPIRED`; `GET /api/v1/foo` con token válido → 404 `NOT_FOUND`; ninguna respuesta trae `Set-Cookie`; todos los 401 cumplen `error-response` con `path` correcto. Verlo fallar. Reemplazar `SecurityConfig` por el definitivo (design D3 y D4). Verificar que `SecurityRulesTest` pasa.
- [x] 3.2 Actualizar los `@WebMvcTest` existentes para la seguridad real: `ProductControllerTest`, `UserControllerTest` y `AuthControllerTest` cambian su `@Import` a `@Import(WebMvcSecurityConfig.class)`; `GlobalExceptionHandlerTest` importa `WebMvcSecurityConfig` y `ErrorHandlingTestController`, y envía un token válido de `TestTokens` en todas sus peticiones (design D7). Verificar que `./gradlew test` pasa completo **sin cambiar ninguna aserción** de esos tests.
- [x] 3.3 Actualizar `HealthIT` según la spec `service-health` modificada: `/actuator/env`, `/actuator/beans`, `/actuator/info` y `/actuator/metrics` sin token → 401 `AUTH_TOKEN_MISSING`; con un token obtenido del login → 404 `NOT_FOUND`; `/actuator/health` sigue 200 sin token. Verificar que `./gradlew integrationTest` pasa completo (`ProductCatalogIT`, `UserRegistrationIT` y `AuthLoginIT` sin cambios).

## 4. Perfil del usuario autenticado

- [x] 4.1 Ampliar `UserServiceImplTest`: `findCurrentUser(1L)` devuelve el `UserResponse`; `findCurrentUser(99L)` lanza `BusinessException` con `AUTH_TOKEN_INVALID`. Verlo fallar. Añadir `findCurrentUser(Long userId)` a `UserService` y `UserServiceImpl` (design D6). Verificar con `./gradlew test --tests UserServiceImplTest`.
- [x] 4.2 Ampliar `UserControllerTest`: `GET /api/v1/users/me` con un token válido de `TestTokens` (usuario 1) → 200 con contrato `user-response` y el service recibe `1L`; el service lanza `AUTH_TOKEN_INVALID` → 401 con ese `code`; sin token → 401 `AUTH_TOKEN_MISSING` sin llamar al service; `PUT /api/v1/users/me` con token → 405. Verlo fallar. Añadir `me` a `UserController` con `@AuthenticationPrincipal AuthUser` y `@SecurityRequirement`. Verificar con `./gradlew test --tests UserControllerTest`.
- [x] 4.3 Añadir a `ArchitectureTest` la regla de design D6 (services sin dependencia de `AuthUser`). Comprobar que falla si `UserService.findCurrentUser` recibiera un `AuthUser` (cambio temporal) y restaurar. Verificar con `./gradlew test --tests ArchitectureTest`.

## 5. Integración de punta a punta

- [x] 5.1 Escribir `TokenAuthenticationIT` (extiende `IntegrationTest`): login real de `standard` y `/users/me` → 200 con `id` 1 y contrato; `buyer` → `id` 3; registro + login + `/users/me` igual al body del registro (limpieza `@registration.it`); cada escenario de §8.2 sobre `/users/me` (sin cabecera, `Basic xyz`, `Bearer `, `abc.def`, otro secreto, payload alterado, `alg: none`, expirado, expirado + alterado) con su `code`; token de un usuario borrado → 401 `AUTH_TOKEN_INVALID`; `GET /api/v1/products` y el login con un token inválido en la cabecera → 200; `GET /api/v1/does-not-exist` sin token → 401 y con token → 404; sin `Set-Cookie`. Verificar con `./gradlew integrationTest --tests TokenAuthenticationIT`.
- [x] 5.2 Ampliar `OpenApiIT`: `/v3/api-docs` incluye `/api/v1/users/me` y esa operación declara el requisito `bearerAuth`. Ejecutar `./gradlew clean check` y verificar que todos los tests pasan.

## Workflow follow-up

- Revisión del usuario y commit (los hace el usuario).
- Archivar el change con `/opsx:archive auth-jwt-filter`: crea `token-authentication` y `user-profile` y aplica los MODIFIED a `error-handling` y `service-health`.
