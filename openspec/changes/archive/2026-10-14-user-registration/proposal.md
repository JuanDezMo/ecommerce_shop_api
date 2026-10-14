# Proposal

## Why

Las apps tienen un formulario de registro con un elemento visual por campo (text fields, DatePicker, dropdown, radio, slider, switch, checkbox) y las suites de pruebas necesitan un endpoint con muchas reglas para practicar escenarios negativos: validación (400), reglas de negocio (422) y conflicto (409). Además, `auth-login` necesita que existan la entidad `User` y su repository antes de poder emitir tokens.

## What Changes

- Nuevo endpoint público `POST /api/v1/users` que registra un usuario y responde **201** con el `UserResponse` (sin contraseña ni hash).
- Validación de cada campo según §6.4 de `docs/shop-backend-arquitectura.md`, con los mensajes fijos de §9.2 → 400 `VALIDATION_ERROR`.
- Reglas de negocio, en este orden, solo si la validación pasa: mayor de 18 años en UTC (422 `UNDERAGE_USER`), términos aceptados (422 `TERMS_NOT_ACCEPTED`) y email no registrado (409 `EMAIL_ALREADY_EXISTS`, también cuando dos registros con el mismo email llegan a la vez).
- El email se normaliza (`trim` + minúsculas) antes de validar; `newsletter` es `false` si no llega. La contraseña se guarda solo como hash BCrypt.
- El usuario nuevo queda `ACTIVE`; `termsAcceptedAt` y `createdAt` salen del `Clock` de la aplicación.
- Paquete `com.shoplab.api.user` siguiendo la plantilla de `product-catalog`: `User`, `UserStatus`, `Plan`, `UserRepository`, `UserService` / `UserServiceImpl`, `UserMapper`, `UserController`, `dto/RegisterUserRequest`, `dto/UserResponse`.
- **No** cambian el `ErrorCode` (los tres códigos ya existen), los contratos (`user-response.schema.json` ya existe) ni la base de datos (tabla `users` de V1).

## Capabilities

### New Capabilities
- `user-registration`: alta pública de usuarios: datos aceptados, validaciones, reglas de negocio, normalización y formato de la respuesta.

### Modified Capabilities
<!-- Ninguna. error-handling ya define el formato de los 400 de validación y de tipo que usa este endpoint. -->

## Impact

- **Código nuevo**: `src/main/java/com/shoplab/api/user/**` y sus tests.
- **Código modificado**: `OpenApiIT` (ruta nueva). Ninguna clase de producción existente cambia.
- **API**: `POST /api/v1/users` público. `GET /api/v1/users` responde 405. `/api/v1/users/me` **no** es parte de este change (llega con `auth-jwt-filter`).
- **Changes posteriores**: `auth-login` reutiliza `User`, `UserStatus` y `UserRepository.findByEmail`; `auth-jwt-filter` añade `GET /users/me` reutilizando `UserMapper`.
- **Pruebas**: los tests de integración que registran usuarios deben borrarlos al terminar, porque `SeedDataIT` comprueba que solo existen los 3 usuarios semilla y `/test/reset` aún no existe.
