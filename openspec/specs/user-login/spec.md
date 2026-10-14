# user-login Specification

## Purpose

Permite que un usuario registrado obtenga, con su email y contraseña, un token de acceso para usar las rutas protegidas, sin que la API revele qué emails existen o están bloqueados.

## Requirements

### Requirement: Login exitoso
`POST /api/v1/auth/login` con el email y la contraseña de un usuario `ACTIVE` SHALL responder 200 con `accessToken`, `tokenType` igual a `Bearer` y `expiresIn` en segundos. La respuesta MUST cumplir `docs/contracts/login-response.schema.json`.

#### Scenario: Usuario semilla
- **WHEN** se envía `{"email":"standard@shoplab.dev","password":"Secret_123"}`
- **THEN** la respuesta es 200, cumple `login-response.schema.json` y `tokenType` es `Bearer`

#### Scenario: Usuario recién registrado
- **WHEN** un usuario se registra con `POST /api/v1/users` y luego hace login con su email y contraseña
- **THEN** la respuesta es 200 con un `accessToken`

#### Scenario: Email con mayúsculas y espacios
- **WHEN** se envía `{"email":"  Standard@ShopLab.dev ","password":"Secret_123"}`
- **THEN** la respuesta es 200

### Requirement: Contenido del token
El `accessToken` SHALL ser un JWT firmado con HS256 cuyos claims son `sub` (id del usuario como texto), `email` (normalizado), `iat` y `exp`, con `exp - iat` igual a `expiresIn`. El token MUST NOT contener la contraseña ni su hash.

#### Scenario: Claims del usuario semilla
- **WHEN** `standard@shoplab.dev` hace login y se decodifica el token
- **THEN** la cabecera tiene `alg` `HS256` y el payload tiene `sub` `"1"`, `email` `standard@shoplab.dev`, e `iat` y `exp` con `exp - iat` igual a `expiresIn`

### Requirement: Duración configurable del token
La duración del token SHALL ser configurable. Por defecto MUST ser 1 hora (`expiresIn` 3600) y en el perfil `testing` MUST ser 15 minutos (`expiresIn` 900).

#### Scenario: Perfil por defecto
- **WHEN** la API corre sin perfil y un usuario hace login
- **THEN** `expiresIn` es `3600`

#### Scenario: Perfil testing
- **WHEN** la API corre con el perfil `testing` y un usuario hace login
- **THEN** `expiresIn` es `900`

### Requirement: Credenciales inválidas sin revelar información
Si el email no existe o la contraseña no coincide, el login SHALL responder 401 `INVALID_CREDENTIALS` con `message` `Invalid email or password`. La respuesta MUST ser idéntica en ambos casos, y una contraseña con formato raro, débil o demasiado larga MUST responder también 401, nunca 400 ni 500.

#### Scenario: Email inexistente
- **WHEN** se envía `{"email":"nobody@shoplab.dev","password":"Secret_123"}`
- **THEN** la respuesta es 401 `INVALID_CREDENTIALS`

#### Scenario: Contraseña incorrecta
- **WHEN** se envía `{"email":"standard@shoplab.dev","password":"Wrong_123"}`
- **THEN** la respuesta es 401 `INVALID_CREDENTIALS` con el mismo `message` que el email inexistente

#### Scenario: Contraseña muy larga
- **WHEN** la contraseña tiene 100 caracteres
- **THEN** la respuesta es 401 `INVALID_CREDENTIALS`

#### Scenario: Contraseña con espacios o caracteres no ASCII
- **WHEN** la contraseña es `Secret 123` o `Señal_123`
- **THEN** la respuesta es 401 `INVALID_CREDENTIALS`

#### Scenario: Email sin formato de email
- **WHEN** se envía `{"email":"not-an-email","password":"Secret_123"}`
- **THEN** la respuesta es 401 `INVALID_CREDENTIALS`, no 400

### Requirement: Usuario bloqueado
Si las credenciales son correctas y el usuario está `LOCKED`, el login SHALL responder 423 `USER_LOCKED` con `message` `User is locked`. Con credenciales incorrectas, un usuario bloqueado MUST recibir 401 `INVALID_CREDENTIALS`.

#### Scenario: Credenciales correctas de un usuario bloqueado
- **WHEN** se envía `{"email":"locked@shoplab.dev","password":"Secret_123"}`
- **THEN** la respuesta es 423 `USER_LOCKED`

#### Scenario: Contraseña incorrecta de un usuario bloqueado
- **WHEN** se envía `{"email":"locked@shoplab.dev","password":"Wrong_123"}`
- **THEN** la respuesta es 401 `INVALID_CREDENTIALS`

### Requirement: Campos obligatorios del login
`email` y `password` SHALL ser obligatorios y no estar en blanco. Si faltan o están en blanco, el login MUST responder 400 `VALIDATION_ERROR` con un `fieldError` `must not be blank` por campo. Ninguna otra validación de formato MUST aplicarse.

#### Scenario: Body vacío
- **WHEN** se envía `{}`
- **THEN** la respuesta es 400 con `fieldErrors` `[{"field":"email","message":"must not be blank"},{"field":"password","message":"must not be blank"}]`

#### Scenario: Contraseña en blanco
- **WHEN** se envía `{"email":"standard@shoplab.dev","password":"   "}`
- **THEN** la respuesta es 400 con `fieldErrors` `[{"field":"password","message":"must not be blank"}]`

### Requirement: Login público y solo por POST
`POST /api/v1/auth/login` SHALL responder sin token. Cualquier otro método sobre esa ruta MUST responder 405 `METHOD_NOT_ALLOWED`.

#### Scenario: Sin cabecera Authorization
- **WHEN** se hace login con credenciales válidas sin cabecera `Authorization`
- **THEN** la respuesta es 200

#### Scenario: GET al login
- **WHEN** se hace `GET /api/v1/auth/login`
- **THEN** la respuesta es 405 `METHOD_NOT_ALLOWED`

### Requirement: Secreto obligatorio para firmar tokens
La API SHALL leer el secreto de firma de la variable de entorno `JWT_SECRET` y MUST negarse a arrancar si falta, está en blanco o mide menos de 32 bytes.

#### Scenario: Sin secreto
- **WHEN** la API arranca sin `JWT_SECRET`
- **THEN** el arranque falla con un error que menciona `jwt.secret`

#### Scenario: Secreto corto
- **WHEN** la API arranca con un `JWT_SECRET` de 31 bytes
- **THEN** el arranque falla con un error que indica que debe tener al menos 32 bytes
