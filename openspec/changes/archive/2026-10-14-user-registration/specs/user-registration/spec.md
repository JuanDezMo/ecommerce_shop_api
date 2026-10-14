# Spec Delta

## Purpose

Permite que cualquier persona cree una cuenta desde las apps, sin autenticación, con reglas de validación y de negocio fijas y verificables, para que después pueda iniciar sesión y comprar.

## ADDED Requirements

### Requirement: Registro exitoso
`POST /api/v1/users` con un body válido SHALL crear el usuario y responder 201 con el usuario creado. La respuesta MUST cumplir `docs/contracts/user-response.schema.json` y el usuario nuevo MUST tener `status` `ACTIVE`.

#### Scenario: Alta con todos los campos
- **WHEN** se envía `{"firstName":"Ana","lastName":"Gomez","email":"ana@example.com","password":"Secret_123","phone":"3001234567","birthDate":"1990-05-20","country":"CO","plan":"PRO","experienceLevel":4,"newsletter":true,"termsAccepted":true}`
- **THEN** la respuesta es 201, cumple `user-response.schema.json`, tiene un `id` nuevo, `status` `ACTIVE` y los mismos valores enviados en `firstName`, `lastName`, `email`, `phone`, `birthDate`, `country`, `plan`, `experienceLevel` y `newsletter`

#### Scenario: Fechas de alta
- **WHEN** un registro es exitoso
- **THEN** `createdAt` y `termsAcceptedAt` son el instante del registro en UTC con formato ISO-8601 terminado en `Z`

### Requirement: La contraseña nunca se expone ni se guarda en claro
La respuesta del registro MUST NOT incluir `password` ni `passwordHash`. La contraseña SHALL guardarse solo como hash BCrypt.

#### Scenario: Respuesta sin contraseña
- **WHEN** un registro es exitoso
- **THEN** el body no contiene las claves `password` ni `passwordHash` ni el texto de la contraseña

#### Scenario: Hash en la base de datos
- **WHEN** se registra un usuario con contraseña `Secret_123`
- **THEN** el valor guardado no es `Secret_123` y verifica `Secret_123` con BCrypt

### Requirement: Campos opcionales
`phone` y `newsletter` SHALL ser opcionales. Sin `phone`, la respuesta MUST incluir `"phone": null`; sin `newsletter` (o con `null`), MUST guardarse y devolverse `false`.

#### Scenario: Sin phone ni newsletter
- **WHEN** un body válido no incluye `phone` ni `newsletter`
- **THEN** la respuesta es 201 con `"phone": null` y `"newsletter": false`

### Requirement: Email normalizado
El email SHALL normalizarse quitando espacios al inicio y al final y pasándolo a minúsculas antes de validarlo, guardarlo o compararlo.

#### Scenario: Mayúsculas y espacios
- **WHEN** se registra con `"email": "  Ana.Gomez@Example.COM "`
- **THEN** la respuesta es 201 con `"email": "ana.gomez@example.com"`

### Requirement: Validación de los campos
Cada campo SHALL validarse según la tabla §6.4 de `docs/shop-backend-arquitectura.md`, con los mensajes de §9.2. Si alguno falla, la respuesta MUST ser 400 `VALIDATION_ERROR` con todos los errores en `fieldErrors`, y MUST NOT crearse ningún usuario.

#### Scenario: Nombre demasiado corto
- **WHEN** `firstName` es `A`
- **THEN** la respuesta es 400 con `{"field":"firstName","message":"size must be between 2 and 50"}`

#### Scenario: Email con formato inválido
- **WHEN** `email` es `bad`
- **THEN** la respuesta es 400 con `{"field":"email","message":"must be a well-formed email address"}`

#### Scenario: Contraseña débil
- **WHEN** `password` es `secret_123` (sin mayúscula)
- **THEN** la respuesta es 400 con `{"field":"password","message":"must contain at least one uppercase letter, one lowercase letter, one number and one special character, without spaces"}`

#### Scenario: Contraseña demasiado larga
- **WHEN** `password` tiene 21 caracteres que cumplen la política
- **THEN** la respuesta es 400 con `{"field":"password","message":"size must be between 8 and 20"}`

#### Scenario: Teléfono con letras o vacío
- **WHEN** `phone` es `300-123` o `""`
- **THEN** la respuesta es 400 con `{"field":"phone","message":"must contain only digits (7 to 15)"}`

#### Scenario: Fecha de nacimiento futura
- **WHEN** `birthDate` es la fecha de hoy o una posterior
- **THEN** la respuesta es 400 con `{"field":"birthDate","message":"must be a date in the past"}`

#### Scenario: País no soportado
- **WHEN** `country` es `BR`
- **THEN** la respuesta es 400 con `{"field":"country","message":"must be one of: US, CO, MX, ES, AR, CL, PE"}`

#### Scenario: Nivel de experiencia fuera de rango
- **WHEN** `experienceLevel` es `0` o `6`
- **THEN** la respuesta es 400 con `{"field":"experienceLevel","message":"must be between 1 and 5"}`

#### Scenario: Campos obligatorios ausentes
- **WHEN** el body es `{}`
- **THEN** la respuesta es 400 con un error por cada campo obligatorio: `birthDate`, `country`, `email`, `experienceLevel`, `firstName`, `lastName`, `password`, `plan` y `termsAccepted`, ordenados por `field`

### Requirement: Mayoría de edad
Si la validación pasa y el usuario tiene menos de 18 años cumplidos, calculados con la fecha actual en UTC, el registro SHALL responder 422 `UNDERAGE_USER` con `message` `User must be at least 18 years old`.

#### Scenario: Cumple 18 hoy
- **WHEN** hoy es `2026-10-11` (UTC) y `birthDate` es `2008-10-11`
- **THEN** la respuesta es 201

#### Scenario: Cumple 18 mañana
- **WHEN** hoy es `2026-10-11` (UTC) y `birthDate` es `2008-10-12`
- **THEN** la respuesta es 422 `UNDERAGE_USER`

#### Scenario: Nacido un 29 de febrero
- **WHEN** hoy es `2026-02-28` (UTC) y `birthDate` es `2008-02-29`
- **THEN** la respuesta es 422 `UNDERAGE_USER`

### Requirement: Términos aceptados
Si la validación pasa, el usuario es mayor de edad y `termsAccepted` es `false`, el registro SHALL responder 422 `TERMS_NOT_ACCEPTED` con `message` `Terms and conditions must be accepted`.

#### Scenario: Términos rechazados
- **WHEN** un body válido trae `"termsAccepted": false`
- **THEN** la respuesta es 422 `TERMS_NOT_ACCEPTED` y no se crea el usuario

#### Scenario: Términos ausentes
- **WHEN** el body no incluye `termsAccepted`
- **THEN** la respuesta es 400 con `{"field":"termsAccepted","message":"must not be null"}`

### Requirement: Email único
Si la validación y las reglas anteriores pasan y ya existe un usuario con el mismo email normalizado, el registro SHALL responder 409 `EMAIL_ALREADY_EXISTS` con `message` `Email is already registered`. Esto MUST cumplirse también cuando dos registros con el mismo email llegan a la vez.

#### Scenario: Email de un usuario semilla
- **WHEN** se registra con `"email": "standard@shoplab.dev"`
- **THEN** la respuesta es 409 `EMAIL_ALREADY_EXISTS`

#### Scenario: Mismo email con otras mayúsculas
- **WHEN** se registra con `"email": "Standard@ShopLab.dev"`
- **THEN** la respuesta es 409 `EMAIL_ALREADY_EXISTS`

#### Scenario: Dos registros simultáneos
- **WHEN** el email no existía al comprobarlo pero otro registro con ese email se guarda antes
- **THEN** la respuesta es 409 `EMAIL_ALREADY_EXISTS`, no 500

### Requirement: Orden de evaluación
La validación de campos SHALL evaluarse antes que las reglas de negocio, y las reglas de negocio MUST evaluarse en este orden: edad, términos, email duplicado. Se responde solo el primer error que aparece.

#### Scenario: Validación antes que negocio
- **WHEN** `email` es `bad` y `birthDate` corresponde a un menor de edad
- **THEN** la respuesta es 400 `VALIDATION_ERROR`

#### Scenario: Menor de edad, sin términos y con email repetido
- **WHEN** un body válido es de un menor de edad, trae `"termsAccepted": false` y usa `standard@shoplab.dev`
- **THEN** la respuesta es 422 `UNDERAGE_USER`

#### Scenario: Sin términos y con email repetido
- **WHEN** un body válido de un adulto trae `"termsAccepted": false` y usa `standard@shoplab.dev`
- **THEN** la respuesta es 422 `TERMS_NOT_ACCEPTED`

### Requirement: Registro público y solo por POST
`POST /api/v1/users` SHALL responder sin token. Cualquier otro método sobre `/api/v1/users` MUST responder 405 `METHOD_NOT_ALLOWED`.

#### Scenario: Sin cabecera Authorization
- **WHEN** se registra un usuario válido sin cabecera `Authorization`
- **THEN** la respuesta es 201

#### Scenario: Listar usuarios no está permitido
- **WHEN** se hace `GET /api/v1/users`
- **THEN** la respuesta es 405 `METHOD_NOT_ALLOWED`
