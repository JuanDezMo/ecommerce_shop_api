# Spec Delta

## Purpose

Define qué rutas de la API exigen un token de acceso y cómo responde la API cuando falta o no es válido, con un `code` distinto por motivo para que las apps y las pruebas sepan exactamente por qué se rechazó la petición.

## ADDED Requirements

### Requirement: Rutas públicas
Las rutas `/api/v1/auth/login`, `/api/v1/users` (solo la ruta exacta), `/api/v1/products`, `/api/v1/products/**`, `/api/v1/test/**`, `/actuator/health`, `/swagger-ui.html`, `/swagger-ui/**` y `/v3/api-docs/**` SHALL atenderse sin token, para cualquier método HTTP.

#### Scenario: Catálogo sin token
- **WHEN** se hace `GET /api/v1/products` sin cabecera `Authorization`
- **THEN** la respuesta es 200

#### Scenario: Método no soportado en una ruta pública
- **WHEN** se hace `DELETE /api/v1/products/1` o `GET /api/v1/users` sin token
- **THEN** la respuesta es 405 `METHOD_NOT_ALLOWED`, no 401

#### Scenario: Ruta de pruebas sin el perfil testing
- **WHEN** la API corre sin el perfil `testing` y se hace `POST /api/v1/test/reset` sin token
- **THEN** la respuesta es 404 `NOT_FOUND`, no 401

### Requirement: Rutas protegidas
Cualquier ruta que no sea pública SHALL exigir un token válido en la cabecera `Authorization: Bearer <token>`, aunque la ruta no exista.

#### Scenario: Perfil sin token
- **WHEN** se hace `GET /api/v1/users/me` sin cabecera `Authorization`
- **THEN** la respuesta es 401

#### Scenario: Subruta de una ruta pública exacta
- **WHEN** se hace `GET /api/v1/users/me` sin token
- **THEN** la respuesta es 401, porque `/api/v1/users` es pública solo como ruta exacta

### Requirement: Token ausente
Si una ruta protegida recibe una petición sin cabecera `Authorization`, o con una cabecera que no empieza por `Bearer `, la API SHALL responder 401 `AUTH_TOKEN_MISSING` con `message` `Authentication token is missing`.

#### Scenario: Sin cabecera
- **WHEN** se hace `GET /api/v1/users/me` sin cabecera `Authorization`
- **THEN** la respuesta es 401 `AUTH_TOKEN_MISSING`

#### Scenario: Otro esquema de autenticación
- **WHEN** se envía `Authorization: Basic xyz`
- **THEN** la respuesta es 401 `AUTH_TOKEN_MISSING`

### Requirement: Token malformado
Si el valor tras `Bearer ` está vacío, son solo espacios o no tiene formato JWT, la API SHALL responder 401 `AUTH_TOKEN_MALFORMED` con `message` `Authentication token is malformed`.

#### Scenario: Bearer sin token
- **WHEN** se envía `Authorization: Bearer ` (sin token)
- **THEN** la respuesta es 401 `AUTH_TOKEN_MALFORMED`

#### Scenario: Texto que no es un JWT
- **WHEN** se envía `Authorization: Bearer abc.def`
- **THEN** la respuesta es 401 `AUTH_TOKEN_MALFORMED`

### Requirement: Token inválido
Si el token tiene formato JWT pero su firma no es válida, no está firmado, se firmó con otro secreto, o su `sub` no es un número o le faltan claims, la API SHALL responder 401 `AUTH_TOKEN_INVALID` con `message` `Authentication token is invalid`.

#### Scenario: Payload alterado
- **WHEN** se cambia el payload de un token válido y se conserva su firma
- **THEN** la respuesta es 401 `AUTH_TOKEN_INVALID`

#### Scenario: Firmado con otro secreto
- **WHEN** el token se firmó con un secreto distinto al de la API
- **THEN** la respuesta es 401 `AUTH_TOKEN_INVALID`

#### Scenario: Token sin firma
- **WHEN** el token tiene `alg` `none`
- **THEN** la respuesta es 401 `AUTH_TOKEN_INVALID`

#### Scenario: Sujeto no numérico
- **WHEN** el token está bien firmado pero `sub` es `abc`
- **THEN** la respuesta es 401 `AUTH_TOKEN_INVALID`

### Requirement: Token expirado
Si el token está bien firmado pero su `exp` ya pasó, la API SHALL responder 401 `AUTH_TOKEN_EXPIRED` con `message` `Authentication token has expired`. Un token expirado y además alterado MUST responder `AUTH_TOKEN_INVALID`, porque la firma se comprueba primero.

#### Scenario: Token vencido
- **WHEN** se usa un token emitido hace 2 horas con una duración de 1 hora
- **THEN** la respuesta es 401 `AUTH_TOKEN_EXPIRED`

#### Scenario: Vencido y alterado
- **WHEN** se usa un token vencido cuyo payload fue alterado
- **THEN** la respuesta es 401 `AUTH_TOKEN_INVALID`

### Requirement: Formato de los rechazos
Todo rechazo por token SHALL usar el formato único de error (`error-response.schema.json`) con `status` 401, el `code` del motivo, `path` de la petición y `fieldErrors` vacío.

#### Scenario: Cuerpo del rechazo
- **WHEN** se hace `GET /api/v1/users/me` sin token
- **THEN** el body cumple `error-response.schema.json`, `path` es `/api/v1/users/me` y `fieldErrors` es `[]`

### Requirement: Token válido
Con un token válido, la petición SHALL llegar a la ruta protegida identificando al usuario del claim `sub`.

#### Scenario: Ruta protegida con token
- **WHEN** se hace `GET /api/v1/users/me` con el token del login de `standard@shoplab.dev`
- **THEN** la respuesta es 200 con el usuario 1

#### Scenario: Ruta protegida inexistente con token
- **WHEN** se hace `GET /api/v1/foo` con un token válido
- **THEN** la respuesta es 404 `NOT_FOUND`

### Requirement: El token no afecta a las rutas públicas
En una ruta pública, la API SHALL ignorar el token enviado, sea válido o no.

#### Scenario: Token inválido en el catálogo
- **WHEN** se hace `GET /api/v1/products` con `Authorization: Bearer abc.def`
- **THEN** la respuesta es 200

#### Scenario: Token expirado en el login
- **WHEN** se hace login con credenciales válidas y un token expirado en la cabecera
- **THEN** la respuesta es 200

### Requirement: Sin sesión en el servidor
La API SHALL ser stateless: MUST NOT crear sesión HTTP ni enviar la cookie `JSESSIONID`.

#### Scenario: Petición autenticada
- **WHEN** se hace `GET /api/v1/users/me` con un token válido
- **THEN** la respuesta no incluye la cabecera `Set-Cookie`
