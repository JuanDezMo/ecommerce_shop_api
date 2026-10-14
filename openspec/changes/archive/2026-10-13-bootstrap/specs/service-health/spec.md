# Spec Delta

## Purpose

Permite a Docker Compose, a las apps y a las suites de prueba saber si la API está lista para recibir peticiones, incluida su conexión a la base de datos, sin exponer información interna del servicio.

## ADDED Requirements

### Requirement: Endpoint de salud público
La API SHALL exponer `GET /actuator/health` sin autenticación, respondiendo 200 con `{"status":"UP"}` cuando la aplicación y la base de datos están disponibles.

#### Scenario: Servicio disponible
- **WHEN** la API y PostgreSQL están en marcha y se hace `GET /actuator/health` sin token
- **THEN** la respuesta es 200 y el body tiene `status` igual a `UP`

### Requirement: Salud refleja la base de datos
Si la base de datos no está disponible, `GET /actuator/health` SHALL responder 503 con `status` distinto de `UP`.

#### Scenario: Base de datos caída
- **WHEN** PostgreSQL no responde y se hace `GET /actuator/health`
- **THEN** la respuesta es 503 y `status` es `DOWN`

### Requirement: Sin detalles ni otros endpoints de Actuator
La respuesta de salud MUST NOT incluir detalles de componentes, y ningún otro endpoint de Actuator SHALL estar expuesto.

#### Scenario: Sin detalles de componentes
- **WHEN** se hace `GET /actuator/health`
- **THEN** el body no contiene la clave `components` ni `details`

#### Scenario: Otros endpoints ocultos
- **WHEN** se hace `GET /actuator/env` o `GET /actuator/beans`
- **THEN** la respuesta es 404 `NOT_FOUND`
