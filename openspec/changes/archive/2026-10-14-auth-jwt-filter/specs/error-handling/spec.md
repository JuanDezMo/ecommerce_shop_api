# Spec Delta

## MODIFIED Requirements

### Requirement: Ruta inexistente
Una petición a una ruta que no existe SHALL responder 404 con `code` `NOT_FOUND` y `message` `Resource not found` cuando la ruta cae dentro de las rutas públicas o la petición lleva un token válido. Fuera de las rutas públicas y sin un token válido, la seguridad se evalúa antes que el enrutado, así que la respuesta MUST ser el 401 `AUTH_TOKEN_*` correspondiente, sin revelar si la ruta existe.

#### Scenario: Ruta desconocida con token válido
- **WHEN** se hace `GET /api/v1/does-not-exist` con un token válido
- **THEN** la respuesta es 404 `NOT_FOUND` con el formato único de error

#### Scenario: Ruta desconocida
- **WHEN** se hace `GET /api/v1/does-not-exist` sin cabecera `Authorization`
- **THEN** la respuesta es 401 `AUTH_TOKEN_MISSING`

#### Scenario: Ruta desconocida dentro de una ruta pública
- **WHEN** se hace `GET /api/v1/products/1/reviews` sin token
- **THEN** la respuesta es 404 `NOT_FOUND`
