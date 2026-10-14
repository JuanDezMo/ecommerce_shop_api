# Proposal

## Why

Las suites de automatización (RestAssured, Appium, Maestro) necesitan tres cosas que la API todavía no ofrece:

- **Datos predecibles antes de cada suite.** Hoy, después de registrar usuarios o comprar, no hay forma de volver a los datos semilla sin recrear el contenedor.
- **Probar la expiración del token sin esperar.** El token dura 15 minutos en el perfil `testing`.
- **Practicar esperas y pantallas de error en la UI** con respuestas lentas o errores forzados.

Es el último change del plan de §16. Además, permite que los tests del backend dejen de mantener una copia manual de los valores semilla.

## What Changes

Solo con el perfil `testing` activo (el que usa `docker compose` por defecto):

- `POST /api/v1/test/reset`: restaura usuarios, productos, stock y órdenes a la semilla de §13, reinicia los ids y responde **204**.
- `POST /api/v1/test/tokens/expired` con `{"email": "..."}`: responde **200** con `{"accessToken", "tokenType": "Bearer"}`, un token ya expirado de ese usuario que la API rechaza con `AUTH_TOKEN_EXPIRED`. Si el email no existe, 404 `USER_NOT_FOUND`; sin email o en blanco, 400 `VALIDATION_ERROR`.
- Cabecera `X-Delay: <ms>`: retrasa la respuesta, hasta un máximo de 10000 ms. Los valores no numéricos o negativos se ignoran.
- Cabecera `X-Force-Error: <status>`: responde ese status (400–599) con `code` `FORCED_ERROR` y el formato único de error. Los valores fuera de rango o no numéricos se ignoran.
- Las dos cabeceras funcionan en cualquier ruta, también sin token, porque se evalúan antes que la seguridad (§7.1, etapa 1). Si llegan juntas, primero espera y luego responde el error.

Fuera del perfil `testing`, nada de esto existe: `/api/v1/test/**` responde 404 y las cabeceras se ignoran.

Además:
- Nuevo contrato `docs/contracts/expired-token-response.schema.json`.
- Los tests de integración del backend usan la misma lógica del reset en lugar de la copia manual de los valores semilla (`support/SeedData`).

## Capabilities

### New Capabilities
- `testing-support`: utilidades exclusivas del perfil `testing`: reset de datos, tokens expirados bajo demanda y cabeceras de retraso y error forzado.

### Modified Capabilities
<!-- Ninguna. token-authentication ya define que /api/v1/test/** es pública y que responde 404 sin el perfil testing. -->

## Impact

- **Código nuevo**: paquete `testsupport` (`TestSupportController`, `TestDataResetService`, `ExpiredTokenFactory`, `ChaosFilter` y sus DTOs). Los cuatro beans llevan `@Profile("testing")`: en `dev` o sin perfil no existe ninguno.
- **Código modificado**: `support/SeedData` (delegará en `TestDataResetService`), `TestingProfileIT`.
- **Documentación**: contrato nuevo y la tabla de §7.5. En §3.3, la dependencia de `UserRepository` pasa de `TestSupportController` a `ExpiredTokenFactory`, porque la regla de ArchUnit prohíbe que un controller use un repository.
- **Operación**: el perfil `testing` nunca debe usarse en un entorno público (§12): `/test/reset` borra todos los datos sin autenticación.
