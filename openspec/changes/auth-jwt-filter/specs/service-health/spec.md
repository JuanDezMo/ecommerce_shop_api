# Spec Delta

## MODIFIED Requirements

### Requirement: Sin detalles ni otros endpoints de Actuator
La respuesta de salud MUST NOT incluir detalles de componentes, y ningún otro endpoint de Actuator SHALL estar expuesto. Como solo `/actuator/health` es pública, una petición a otro endpoint de Actuator sin token MUST responder 401 `AUTH_TOKEN_MISSING`, y con un token válido MUST responder 404 `NOT_FOUND`.

#### Scenario: Sin detalles de componentes
- **WHEN** se hace `GET /actuator/health`
- **THEN** el body no contiene la clave `components` ni `details`

#### Scenario: Otros endpoints ocultos
- **WHEN** se hace `GET /actuator/env` o `GET /actuator/beans` sin cabecera `Authorization`
- **THEN** la respuesta es 401 `AUTH_TOKEN_MISSING`

#### Scenario: Otros endpoints ocultos con token válido
- **WHEN** se hace `GET /actuator/env` o `GET /actuator/beans` con un token válido
- **THEN** la respuesta es 404 `NOT_FOUND`
