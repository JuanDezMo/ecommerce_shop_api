# testing-support Specification

## Purpose

Da a las suites de automatización herramientas para preparar datos conocidos, obtener tokens expirados sin esperar y simular respuestas lentas o fallidas, solo cuando la API corre con el perfil `testing`.

## Requirements

### Requirement: Utilidades solo en el perfil testing
Las rutas `/api/v1/test/**` y las cabeceras `X-Delay` y `X-Force-Error` SHALL existir solo con el perfil `testing` activo. Sin ese perfil, `/api/v1/test/**` MUST responder 404 `NOT_FOUND` y las cabeceras MUST ignorarse.

#### Scenario: Reset sin el perfil testing
- **WHEN** la API corre sin el perfil `testing` y se hace `POST /api/v1/test/reset`
- **THEN** la respuesta es 404 `NOT_FOUND` y los datos no cambian

#### Scenario: Cabecera de error sin el perfil testing
- **WHEN** la API corre sin el perfil `testing` y se hace `GET /api/v1/products` con `X-Force-Error: 500`
- **THEN** la respuesta es 200

### Requirement: Restaurar los datos semilla
Con el perfil `testing`, `POST /api/v1/test/reset` SHALL borrar todos los usuarios, productos y órdenes, volver a cargar los datos semilla y reiniciar los ids, y responder 204 sin body, y MUST NOT exigir token.

#### Scenario: Reset después de cambios
- **WHEN** se registra un usuario, se compra la última unidad del producto 5 y luego se hace `POST /api/v1/test/reset`
- **THEN** la respuesta es 204, el usuario registrado ya no existe, el producto 5 vuelve a tener stock 1 y `buyer@shoplab.dev` vuelve a tener solo sus 2 órdenes

#### Scenario: Ids reiniciados
- **WHEN** después de un reset se registra un usuario nuevo
- **THEN** su `id` es 4

#### Scenario: Reset idempotente
- **WHEN** se hace `POST /api/v1/test/reset` dos veces seguidas
- **THEN** ambas responden 204 y los datos son los de la semilla

#### Scenario: Solo POST
- **WHEN** se hace `GET /api/v1/test/reset`
- **THEN** la respuesta es 405 `METHOD_NOT_ALLOWED`

### Requirement: Token expirado bajo demanda
Con el perfil `testing`, `POST /api/v1/test/tokens/expired` con `{"email": "..."}` SHALL responder 200 con `accessToken` y `tokenType` `Bearer`, donde `accessToken` es un token de ese usuario, bien firmado y ya expirado. La respuesta MUST cumplir `docs/contracts/expired-token-response.schema.json`. El email se normaliza igual que en el login y la ruta no exige token.

#### Scenario: Token expirado de un usuario semilla
- **WHEN** se pide un token expirado para `standard@shoplab.dev` y se usa en `GET /api/v1/users/me`
- **THEN** la petición de token responde 200 y `/users/me` responde 401 `AUTH_TOKEN_EXPIRED`

#### Scenario: Email con mayúsculas
- **WHEN** se pide un token expirado para `  Standard@ShopLab.dev `
- **THEN** la respuesta es 200

### Requirement: Errores del token expirado bajo demanda
Si el email no corresponde a ningún usuario, la ruta SHALL responder 404 `USER_NOT_FOUND` con `message` `User not found`. Si falta el email o está en blanco, MUST responder 400 `VALIDATION_ERROR` con `{"field":"email","message":"must not be blank"}`.

#### Scenario: Email inexistente
- **WHEN** se pide un token expirado para `nobody@shoplab.dev`
- **THEN** la respuesta es 404 `USER_NOT_FOUND`

#### Scenario: Sin email
- **WHEN** el body es `{}` o `{"email":"  "}`
- **THEN** la respuesta es 400 con `fieldErrors` `[{"field":"email","message":"must not be blank"}]`

### Requirement: Retraso de la respuesta
Con el perfil `testing`, una petición con `X-Delay: <n>` SHALL responder después de esperar `n` milisegundos, con un máximo de 10000. Un valor no numérico o negativo MUST ignorarse.

#### Scenario: Retraso corto
- **WHEN** se hace `GET /api/v1/products` con `X-Delay: 500`
- **THEN** la respuesta es 200 y tarda al menos 500 ms

#### Scenario: Retraso mayor que el máximo
- **WHEN** el valor de `X-Delay` es `50000`
- **THEN** la API espera 10000 ms, no 50000

#### Scenario: Valor inválido
- **WHEN** `X-Delay` es `abc` o `-5`
- **THEN** la cabecera se ignora y la respuesta llega sin retraso

### Requirement: Error forzado
Con el perfil `testing`, una petición con `X-Force-Error: <status>`, donde `status` es un entero entre 400 y 599, SHALL responder ese status con el formato único de error, `code` `FORCED_ERROR`, `message` `Forced error for testing` y `fieldErrors` vacío, sin ejecutar la petición. Un valor fuera de ese rango o no numérico MUST ignorarse.

#### Scenario: Error 500 forzado
- **WHEN** se hace `GET /api/v1/products` con `X-Force-Error: 500`
- **THEN** la respuesta es 500 `FORCED_ERROR`, cumple `error-response.schema.json` y `path` es `/api/v1/products`

#### Scenario: Error forzado en una petición que modificaría datos
- **WHEN** se crea una orden válida con `X-Force-Error: 503`
- **THEN** la respuesta es 503 `FORCED_ERROR` y no se crea la orden ni se descuenta stock

#### Scenario: Valores ignorados
- **WHEN** `X-Force-Error` es `200`, `abc` o `700`
- **THEN** la cabecera se ignora y la petición se procesa normalmente

### Requirement: Cabeceras antes que la seguridad
`X-Delay` y `X-Force-Error` SHALL evaluarse antes que la autenticación, así que también funcionan en rutas protegidas sin token. Si llegan juntas, la API MUST esperar primero y responder después el error forzado.

#### Scenario: Error forzado sin token en ruta protegida
- **WHEN** se hace `GET /api/v1/users/me` sin token y con `X-Force-Error: 418`
- **THEN** la respuesta es 418 `FORCED_ERROR`, no 401

#### Scenario: Retraso y error juntos
- **WHEN** se envían `X-Delay: 300` y `X-Force-Error: 502`
- **THEN** la respuesta es 502 `FORCED_ERROR` y tarda al menos 300 ms
