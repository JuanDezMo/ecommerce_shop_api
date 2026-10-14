# Spec Delta

## Purpose

Define cómo responde la API cuando una petición no se puede atender: un único formato JSON con un código estable, un mensaje fijo en inglés y el detalle de los campos inválidos, para que las apps y las pruebas automatizadas puedan verificar el motivo exacto del error.

## ADDED Requirements

### Requirement: Formato único de error
Toda respuesta de error (4xx y 5xx) SHALL ser un objeto JSON con exactamente los campos `timestamp` (ISO-8601 en UTC), `status` (igual al código HTTP), `code` (un valor del catálogo de errores), `message` (texto en inglés no vacío), `path` (ruta de la petición) y `fieldErrors` (lista, vacía cuando el error no es de validación). La respuesta MUST cumplir `docs/contracts/error-response.schema.json`.

#### Scenario: Error de negocio sin campos
- **WHEN** una petición termina en un error de regla de negocio
- **THEN** la respuesta tiene el status HTTP de ese error, los seis campos del formato y `fieldErrors` es `[]`

#### Scenario: No se exponen detalles internos
- **WHEN** ocurre cualquier error
- **THEN** la respuesta no contiene stack traces, nombres de clases Java ni mensajes de excepciones internas

### Requirement: Catálogo de códigos de error
Cada `code` SHALL tener un status HTTP y un mensaje fijos, según el catálogo de §9.1 de `docs/shop-backend-arquitectura.md`. Los mensajes que incluyen un identificador MUST contener el id real del recurso afectado.

#### Scenario: Mensaje con identificador
- **WHEN** un error de "producto no encontrado" se produce para el producto 99
- **THEN** la respuesta tiene status 404, `code` `PRODUCT_NOT_FOUND` y `message` `Product 99 not found`

#### Scenario: Mensaje fijo
- **WHEN** se produce un error `USER_LOCKED`
- **THEN** la respuesta tiene status 423 y `message` `User is locked`

### Requirement: Errores de validación de campos
Cuando uno o más campos del body incumplen sus reglas de validación, la API SHALL responder 400 con `code` `VALIDATION_ERROR`, `message` `Request has invalid fields` y un elemento en `fieldErrors` por cada regla incumplida. `fieldErrors` MUST estar ordenado por `field` y luego por `message`.

#### Scenario: Varios campos inválidos
- **WHEN** un body tiene `email` con formato inválido y no incluye `country`
- **THEN** la respuesta es 400 `VALIDATION_ERROR` con `fieldErrors` `[{"field":"country","message":"must not be blank"},{"field":"email","message":"must be a well-formed email address"}]`

#### Scenario: Un campo incumple dos reglas
- **WHEN** `password` es `abc`
- **THEN** `fieldErrors` contiene dos elementos con `field` `password`, ordenados alfabéticamente por `message`

#### Scenario: Campo vacío incumple varias reglas
- **WHEN** `country` es `""`
- **THEN** `fieldErrors` contiene `must be one of: US, CO, MX, ES, AR, CL, PE` y `must not be blank` para `country`, en ese orden

#### Scenario: Campo anidado o en una lista
- **WHEN** el segundo ítem de `items` tiene `quantity` 0
- **THEN** el `field` del error es `items[1].quantity`

### Requirement: Errores de tipo en el body
Cuando un valor del body no se puede convertir al tipo esperado, la API SHALL responder 400 `VALIDATION_ERROR` con `message` `Request has invalid fields` y exactamente un elemento en `fieldErrors` para el primer campo con tipo inválido. La API MUST NOT convertir texto a número o booleano ni decimales a enteros.

#### Scenario: Enum inválido
- **WHEN** el body trae `"plan": "GOLD"` y los valores permitidos son `BASIC` y `PRO`
- **THEN** `fieldErrors` es `[{"field":"plan","message":"must be one of: BASIC, PRO"}]`

#### Scenario: Número enviado como texto
- **WHEN** el body trae `"quantity": "2"` en el primer ítem
- **THEN** `fieldErrors` es `[{"field":"items[0].quantity","message":"must be an integer"}]`

#### Scenario: Decimal donde se espera un entero
- **WHEN** el body trae `"quantity": 1.5` en el primer ítem
- **THEN** `fieldErrors` es `[{"field":"items[0].quantity","message":"must be an integer"}]`

#### Scenario: Booleano enviado como texto
- **WHEN** el body trae `"termsAccepted": "true"`
- **THEN** `fieldErrors` es `[{"field":"termsAccepted","message":"must be true or false"}]`

#### Scenario: Fecha con formato inválido
- **WHEN** el body trae `"birthDate": "20-05-1990"`
- **THEN** `fieldErrors` es `[{"field":"birthDate","message":"must be a valid date (yyyy-MM-dd)"}]`

### Requirement: Body ausente o mal formado
Cuando el body falta, está vacío, no es JSON válido o no tiene la forma de un objeto, la API SHALL responder 400 `VALIDATION_ERROR` con `message` `Malformed JSON request` y `fieldErrors` vacío.

#### Scenario: JSON cortado
- **WHEN** el body es `{"items": [`
- **THEN** la respuesta es 400 `VALIDATION_ERROR` con `message` `Malformed JSON request` y `fieldErrors` `[]`

#### Scenario: Body vacío
- **WHEN** un endpoint que espera body recibe una petición sin body
- **THEN** la respuesta es 400 `VALIDATION_ERROR` con `message` `Malformed JSON request`

#### Scenario: Array en lugar de objeto
- **WHEN** el body es `[]` y el endpoint espera un objeto
- **THEN** la respuesta es 400 `VALIDATION_ERROR` con `message` `Malformed JSON request`

### Requirement: Campos desconocidos
La API SHALL ignorar los campos del body que no forman parte del contrato del endpoint.

#### Scenario: Campo extra
- **WHEN** un body válido incluye además `"foo": 1`
- **THEN** la petición se procesa como si `foo` no existiera

### Requirement: Parámetro de ruta con tipo inválido
Cuando un parámetro de la ruta no se puede convertir a su tipo, la API SHALL responder 400 `VALIDATION_ERROR` con un elemento en `fieldErrors` cuyo `field` es el nombre del parámetro.

#### Scenario: Id no numérico
- **WHEN** se pide un recurso con id `abc` y el id es numérico
- **THEN** la respuesta es 400 con `fieldErrors` `[{"field":"id","message":"must be an integer"}]`

### Requirement: Ruta inexistente
Una petición a una ruta que no existe SHALL responder 404 con `code` `NOT_FOUND` y `message` `Resource not found`.

#### Scenario: Ruta desconocida
- **WHEN** se hace `GET /api/v1/does-not-exist`
- **THEN** la respuesta es 404 `NOT_FOUND` con el formato único de error

### Requirement: Método HTTP no soportado
Una petición con un método que la ruta no admite SHALL responder 405 con `code` `METHOD_NOT_ALLOWED` y `message` `HTTP method not allowed`.

#### Scenario: Método incorrecto
- **WHEN** se hace `DELETE` sobre una ruta que solo admite `GET`
- **THEN** la respuesta es 405 `METHOD_NOT_ALLOWED`

### Requirement: Tipo de contenido no soportado
Una petición con body cuyo `Content-Type` no es `application/json` SHALL responder 415 con `code` `UNSUPPORTED_MEDIA_TYPE` y `message` `Content-Type must be application/json`.

#### Scenario: Body en texto plano
- **WHEN** se envía un body con `Content-Type: text/plain` a un endpoint que espera JSON
- **THEN** la respuesta es 415 `UNSUPPORTED_MEDIA_TYPE`

### Requirement: Error inesperado
Cualquier error no previsto SHALL responder 500 con `code` `INTERNAL_ERROR` y `message` `Unexpected error`, y el detalle MUST quedar solo en el log del servidor.

#### Scenario: Falla no controlada
- **WHEN** un endpoint falla por una causa no prevista
- **THEN** la respuesta es 500 `INTERNAL_ERROR` con `message` `Unexpected error` y sin detalles internos
