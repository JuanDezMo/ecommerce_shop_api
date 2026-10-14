# user-profile Specification

## Purpose

Permite que la app muestre los datos del usuario que inició sesión, identificándolo solo por su token, sin exponer datos de otros usuarios ni la contraseña.

## Requirements

### Requirement: Perfil del usuario autenticado
`GET /api/v1/users/me` con un token válido SHALL responder 200 con los datos del usuario del token. La respuesta MUST cumplir `docs/contracts/user-response.schema.json` y MUST NOT incluir `password` ni `passwordHash`.

#### Scenario: Usuario semilla
- **WHEN** `standard@shoplab.dev` hace login y luego `GET /api/v1/users/me` con su token
- **THEN** la respuesta es 200 con `id` 1, `email` `standard@shoplab.dev`, `firstName` `Standard`, `phone` `null` y `status` `ACTIVE`, y cumple `user-response.schema.json`

#### Scenario: Usuario recién registrado
- **WHEN** un usuario se registra, hace login y consulta `GET /api/v1/users/me`
- **THEN** la respuesta es igual al body que devolvió el registro

### Requirement: El perfil siempre es del dueño del token
`GET /api/v1/users/me` SHALL devolver solo el usuario identificado por el token y MUST NOT aceptar ningún parámetro para pedir otro usuario.

#### Scenario: Dos usuarios distintos
- **WHEN** `standard@shoplab.dev` y `buyer@shoplab.dev` consultan `/users/me` cada uno con su token
- **THEN** cada uno recibe su propio `id` (1 y 3)

### Requirement: Usuario del token inexistente
Si el usuario del token ya no existe, `GET /api/v1/users/me` SHALL responder 401 `AUTH_TOKEN_INVALID` con `message` `Authentication token is invalid`.

#### Scenario: Usuario borrado
- **WHEN** se usa un token válido de un usuario que fue borrado de la base de datos
- **THEN** la respuesta es 401 `AUTH_TOKEN_INVALID`

### Requirement: Perfil solo por GET
`/api/v1/users/me` SHALL admitir solo `GET`. Con un token válido, cualquier otro método MUST responder 405 `METHOD_NOT_ALLOWED`.

#### Scenario: Intento de modificar el perfil
- **WHEN** se hace `PUT /api/v1/users/me` con un token válido
- **THEN** la respuesta es 405 `METHOD_NOT_ALLOWED`
