# Proposal

## Why

Ya existen usuarios (semilla y registrados), pero no hay forma de identificarse: las apps no pueden pasar de la pantalla de login y `orders` necesitará un token. Además, el login es el endpoint de seguridad que más se practica en pruebas de API (credenciales inválidas, usuario bloqueado, no revelar qué emails existen). Por eso se separó del filtro JWT (§16): primero se **emite** el token; el change `auth-jwt-filter` lo **valida**.

## What Changes

- Nuevo endpoint público `POST /api/v1/auth/login` que responde **200** con `{"accessToken", "tokenType": "Bearer", "expiresIn"}` (contrato `login-response.schema.json`).
- Errores del login según §7.2 y §8.3: 400 `VALIDATION_ERROR` si falta `email` o `password` (o están en blanco); 401 `INVALID_CREDENTIALS` para email inexistente, contraseña incorrecta o cualquier contraseña que no cumpla la política (sin pasarla a BCrypt); 423 `USER_LOCKED` solo con credenciales correctas.
- El token es un JWT HS256 con los claims `sub` (id del usuario), `email`, `iat` y `exp`. Su duración es configurable con `jwt.ttl`: 1 h por defecto y 15 min en el perfil `testing` (`expiresIn: 900`).
- El secreto se lee de `JWT_SECRET`. Si falta o mide menos de 32 bytes, **la aplicación no arranca** (fail fast).
- Clases nuevas: `auth/AuthController`, `AuthService` / `AuthServiceImpl`, `auth/dto/LoginRequest` y `LoginResponse`; `security/JwtProperties`, `JwtService` (solo `generateToken`) y `JjwtService`. `UserRepository` gana `findByEmail`.
- **Infraestructura de pruebas**: los arranques de `@SpringBootTest` (`IntegrationTest` y `HealthDatabaseDownIT`) reciben un secreto de prueba, porque sin él la aplicación ya no arranca.

## Capabilities

### New Capabilities
- `user-login`: inicio de sesión con email y contraseña: respuesta con el token, errores sin revelar información, contenido y duración del token, y la configuración obligatoria del secreto.

### Modified Capabilities
<!-- Ninguna. El rechazo de tokens (AUTH_TOKEN_*) y las rutas protegidas llegan con auth-jwt-filter. -->

## Impact

- **Código nuevo**: paquete `auth` y tres clases en `security`.
- **Código modificado**: `UserRepository` (`findByEmail`), `application.yml` (`jwt.secret`, `jwt.ttl`), `application-testing.yml` (`jwt.ttl: 15m`), `IntegrationTest` y `HealthDatabaseDownIT` (secreto de prueba), `OpenApiIT` (ruta nueva).
- **API**: `POST /api/v1/auth/login` público; `GET /api/v1/auth/login` responde 405.
- **Operación**: quien arranque la API sin `JWT_SECRET` (por ejemplo desde el IDE con el perfil `dev`) verá un error al arrancar. `docker-compose.yml` y `.env.example` ya lo definen.
- **Changes posteriores**: `auth-jwt-filter` añade `JwtService.parse` y el filtro; `testing-profile` usa `JjwtService` con un `Clock` en el pasado para crear tokens expirados.
