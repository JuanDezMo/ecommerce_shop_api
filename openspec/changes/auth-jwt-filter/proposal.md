# Proposal

## Why

`auth-login` ya emite tokens, pero la API todavía no los exige: el `SecurityConfig` provisional deja todo público. `orders` necesita saber quién hace la petición, y las suites de pruebas necesitan los escenarios de seguridad de §8.2 (sin token, token malformado, firma alterada, token expirado), cada uno con su propio `code`. También falta `GET /users/me`, la primera ruta protegida, para que la app muestre el perfil del usuario autenticado.

## What Changes

- **BREAKING (comportamiento):** se reemplaza el `SecurityConfig` provisional por el definitivo de §7.1. Solo quedan públicas `/api/v1/auth/login`, `/api/v1/users` (ruta exacta), `/api/v1/products` y `/api/v1/products/**`, `/api/v1/test/**`, `/actuator/health`, Swagger y `/error`, para todos los métodos HTTP. Todo lo demás exige un token válido.
- Las peticiones a rutas protegidas sin un token válido responden **401** con el formato único de error y un `code` que indica el motivo: `AUTH_TOKEN_MISSING`, `AUTH_TOKEN_MALFORMED`, `AUTH_TOKEN_INVALID` o `AUTH_TOKEN_EXPIRED`.
- **BREAKING (comportamiento):** una ruta inexistente fuera de las públicas responde **401** sin token (antes 404), y los endpoints ocultos de Actuator (`/actuator/env`…) responden 401 sin token (antes 404). Con token válido siguen respondiendo 404. Es el comportamiento estándar de Spring Security que §7.1 adopta para no revelar qué rutas existen.
- En las rutas públicas el token se ignora: un token inválido en `/products` no cambia la respuesta.
- Nuevo endpoint protegido `GET /api/v1/users/me`: devuelve el `UserResponse` del usuario del token; si ese usuario ya no existe, 401 `AUTH_TOKEN_INVALID`.
- Clases nuevas en `security`: `AuthUser`, `InvalidTokenException`, `JwtAuthenticationFilter`, `JwtAuthenticationEntryPoint` (401 en JSON) y `JsonAccessDeniedHandler` (403 en JSON, reservado). `JwtService` gana `parse(token)`.
- Una regla nueva de ArchUnit: los services no dependen de `AuthUser` (reciben `Long userId`, §3.2).

## Capabilities

### New Capabilities
- `token-authentication`: qué rutas son públicas y cómo se rechaza una petición a una ruta protegida según el estado del token.
- `user-profile`: consulta del perfil del usuario autenticado (`GET /users/me`).

### Modified Capabilities
- `error-handling`: el requisito *Ruta inexistente* distingue entre rutas públicas, rutas protegidas sin token (401) y con token válido (404).
- `service-health`: el requisito *Sin detalles ni otros endpoints de Actuator* pasa a responder 401 sin token y 404 con token válido para los endpoints ocultos.

## Impact

- **Código nuevo**: 5 clases en `security`; `GET /users/me` en `UserController` y `UserService.findCurrentUser`.
- **Código modificado**: `SecurityConfig` (definitivo), `JwtService` / `JjwtService` (`parse`), `UserController`, `UserService` / `UserServiceImpl`, `ArchitectureTest`.
- **Tests existentes afectados**: los `@WebMvcTest` que importan `SecurityConfig` (`Product`, `User`, `Auth` y `GlobalExceptionHandlerTest`) pasan a necesitar `JwtService` y el entry point; `GlobalExceptionHandlerTest` usa rutas que ahora son protegidas; `HealthIT` espera 404 en `/actuator/env`.
- **API**: `ProductCatalogIT`, `UserRegistrationIT` y `AuthLoginIT` ya llaman sin `Authorization` y deben seguir pasando sin cambios: son la garantía de que las rutas públicas siguen abiertas.
- **Changes posteriores**: `orders` usa `@AuthenticationPrincipal AuthUser` y `JwtService`; `testing-profile` genera tokens expirados con `JjwtService` y un reloj en el pasado.
