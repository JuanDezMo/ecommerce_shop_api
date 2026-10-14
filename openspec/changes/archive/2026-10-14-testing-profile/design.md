# Design

## Context

- `application-testing.yml` ya existe (`jwt.ttl: 15m`) y `docker compose` arranca con ese perfil por defecto. `TestingProfileIT` comprueba que la aplicación levanta con él.
- `SecurityConfig` ya deja `/api/v1/test/**` en `permitAll`, y `TokenAuthenticationIT` comprueba que sin el perfil `testing` `POST /api/v1/test/reset` responde 404.
- `ErrorCode` ya tiene `USER_NOT_FOUND` (404) y `FORCED_ERROR`. `security/ErrorResponseWriter` escribe un `ErrorResponse` desde un filtro, pero es de paquete y usa el status del `ErrorCode`.
- `JjwtService` acepta un `Clock`: con un reloj en el pasado emite tokens ya expirados (§16).
- `V4__seed_data.sql` inserta sin ids explícitos y no tiene sentencias de Flyway, precisamente para poder ejecutarlo de nuevo (§13).
- Los tests de integración usan `support/SeedData.restoreOrdersAndStock`, que repite a mano el stock de §13.
- La regla `controllersOnlyTalkToServiceInterfaces` de `ArchitectureTest` impide que un controller dependa de un `*Repository`, aunque §3.3 menciona "`TestSupportController` → `UserRepository`".

Ver `proposal.md` para la motivación y `specs/testing-support/spec.md` para el comportamiento.

## Goals / Non-Goals

**Goals:**
- Que todo lo de este change quede apagado fuera del perfil `testing`, con `@Profile("testing")` en cada bean.
- Una única fuente de verdad para la semilla: el mismo `V4__seed_data.sql` que usa Flyway, también para el reset y para los tests del backend.

**Non-Goals:**
- Proteger `/test/**` con autenticación: es un backend de práctica y el perfil `testing` nunca debe exponerse en público (§12).
- Simular otros fallos (cortes de red, respuestas parciales).

## Decisions

### D1. Clases del paquete `testsupport`

```
testsupport/
├── TestSupportController.java      ← @RestController @Profile("testing"): /api/v1/test/reset y /tokens/expired
├── TestDataResetService.java       ← @Service @Profile("testing"): TRUNCATE + V4__seed_data.sql
├── ExpiredTokenFactory.java        ← @Component @Profile("testing"): busca el usuario y firma con un reloj en el pasado
├── ChaosFilter.java                ← @Component @Profile("testing"): X-Delay y X-Force-Error
└── dto/
    ├── ExpiredTokenRequest.java    ← record(email) con @NotBlank; normaliza como LoginRequest
    └── ExpiredTokenResponse.java   ← record(accessToken, tokenType)
```

- **`ExpiredTokenFactory` usa `UserRepository`, no el controller.** Así se cumple la regla de ArchUnit y el controller solo coordina. Se actualiza la línea de §3.3 del documento de arquitectura para que diga `ExpiredTokenFactory` → `UserRepository`. *Alternativa descartada:* excluir `testsupport` de la regla, que abre una excepción justo en el código que los juniors copian menos y entienden peor.
- **Las cuatro clases con bean llevan `@Profile("testing")`** (controller, servicio de reset, fábrica de tokens y filtro). Fuera de ese perfil el paquete `testsupport` no crea ningún bean: `/api/v1/test/**` responde 404 porque no hay controller, y las cabeceras se ignoran porque no hay filtro.
- `ExpiredTokenFactory` no termina en `Service`, así que no le aplican las reglas de ArchUnit de los services de negocio. `TestDataResetService` sí termina en `Service`, pero no es un `ServiceImpl` ni depende de `AuthUser`, así que cumple todas las reglas.

### D2. `TestDataResetService`

```java
@Service
@Profile("testing")
public class TestDataResetService {

    @Transactional
    public void reset() {
        // TRUNCATE y script en la MISMA conexión
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("TRUNCATE order_items, orders, users, products RESTART IDENTITY");
            }
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V4__seed_data.sql"));
            return null;
        });
    }
}
```

- Un solo `TRUNCATE` con las cuatro tablas: PostgreSQL resuelve las claves foráneas entre ellas sin `CASCADE`, y `RESTART IDENTITY` reinicia los ids (el primer usuario registrado después tiene id 4).
- **Lleva `@Profile("testing")`, igual que el resto de `testsupport`**: fuera de ese perfil no existe ningún bean capaz de vaciar la base de datos, ni siquiera sin exponer. `@Profile` solo decide si Spring crea el bean; no impide que `support/SeedData` lo instancie con `new TestDataResetService(jdbc)` en los tests sin perfil (D5). *Corrección hecha en la revisión del change:* la primera versión de este design lo dejaba sin `@Profile` con el argumento contrario, que era incorrecto.
- **Misma conexión para el `TRUNCATE` y el script** (`ConnectionCallback`):
  - En la aplicación, `@Transactional` vincula esa conexión a la transacción: si el script falla, el `TRUNCATE` también se deshace y la base no queda vacía.
  - Creado con `new` (en los tests), Spring no aplica `@Transactional`: cada sentencia se confirma por separado. Es aceptable porque los tests se ejecutan uno tras otro y el script es el mismo que ya validó Flyway.
- Depende solo de `JdbcTemplate`.

### D3. `ExpiredTokenFactory`

- `create(email)`: `userRepository.findByEmail(email)` → `USER_NOT_FOUND` si no existe.
- Reloj en el pasado: `Clock.offset(clock, ttl.plusMinutes(1).negated())`. El token se emite "hace TTL + 1 minuto", así que ya venció hace 1 minuto. Se firma con `new JjwtService(jwtProperties, pastClock)`, el mismo código que el login: si la API lo valida, responde `AUTH_TOKEN_EXPIRED` y nunca `INVALID`.
- La respuesta es `ExpiredTokenResponse(accessToken, "Bearer")`, sin `expiresIn` (decisión del usuario al revisar el change). Su contrato es el nuevo `docs/contracts/expired-token-response.schema.json` (draft-04, `additionalProperties: false`, `tokenType` con `enum: ["Bearer"]`).

### D4. `ChaosFilter`

- `OncePerRequestFilter` con `@Component`, `@Profile("testing")` y `@Order(Ordered.HIGHEST_PRECEDENCE)`. Spring Boot lo registra como filtro de servlet **antes** de la cadena de Spring Security (orden -100), que es la etapa 1 de §7.1. A diferencia de `JwtAuthenticationFilter`, aquí sí queremos que sea un bean: debe ir fuera y antes de la seguridad.
- Dos métodos estáticos y puros, probados de forma unitaria:
  - `delayMillis(String header)`: `0` si falta, no es numérico o es negativo; el valor recortado a 10000.
  - `forcedStatus(String header)`: el entero si está entre 400 y 599; `null` en otro caso.
- Flujo: si hay retraso, `sleep(ms)`; después, si hay status forzado, escribe el `ErrorResponse` y **no** llama a `chain.doFilter` (la petición no llega al controller ni modifica datos); si no, sigue la cadena.
- `sleep` es un método `protected` que llama a `Thread.sleep`. El test unitario lo sobrescribe para registrar el valor sin esperar 10 s.
- Para escribir el JSON se reutiliza `security/ErrorResponseWriter`: se hace `public` y gana una sobrecarga con el status explícito (`FORCED_ERROR` se declara con 500, pero responde el status de la cabecera, §9.1). *Alternativa descartada:* copiar el escritor en `testsupport`.

### D5. `SeedData` delega en el reset

`support/SeedData.restoreOrdersAndStock(jdbc)` pasa a ser `new TestDataResetService(jdbc).reset()`: se crea con `new` porque esos tests corren sin el perfil `testing` y el bean no existe (D2). Se eliminan los valores de stock escritos a mano. Los tres tests que lo usan (`ProductRepositoryIT`, `OrderRepositoryIT` y `OrdersIT`) no cambian. *Consecuencia aceptada:* también se borran los usuarios registrados y se reinician los ids, lo que deja la base todavía más limpia.

### D6. Tests

| Test | Tipo | Qué verifica |
|---|---|---|
| `ChaosFilterTest` | Unitario | `delayMillis` (`500`, `50000`→10000, `abc`, `-5`, ausente); `forcedStatus` (`500`, `418`, `200`, `700`, `abc`); el filtro con error forzado escribe el `ErrorResponse` con ese status y no llama a la cadena; retraso y error juntos duermen y luego responden |
| `ExpiredTokenFactoryTest` | Unitario | El token de un usuario existente es rechazado por un `JjwtService` real con `AUTH_TOKEN_EXPIRED`; email inexistente → `USER_NOT_FOUND` |
| `TestDataResetIT` | `IntegrationTest` | Tras crear un usuario, comprar y vender la última unidad, `reset()` deja exactamente la semilla (usa las mismas consultas que `SeedDataIT`) y el siguiente usuario registrado tiene id 4 |
| `TestSupportIT` | `IntegrationTest` + `@ActiveProfiles("testing")` | Escenarios HTTP de la spec: 204 del reset, 405, token expirado → `/users/me` 401 `EXPIRED`, 404 y 400 del token, `X-Delay` corto, `X-Force-Error` en ruta pública, protegida sin token y en `POST /orders` sin efectos, valores ignorados, retraso + error |
| `TokenAuthenticationIT` (existente) | `IntegrationTest` sin perfil | Ya comprueba el 404 de `/test/reset` sin perfil; se añade que `X-Force-Error` se ignora sin el perfil |

## Risks / Trade-offs

- [`/test/reset` borra todo sin autenticación] → Solo existe con el perfil `testing`; §12 y `.env.example` advierten que ese perfil no debe exponerse. Es el comportamiento pedido.
- [`X-Delay` alarga las suites] → Máximo 10 s por petición. Los tests del backend usan 300–500 ms, y el recorte a 10000 se prueba sin dormir (D4).
- [Un `TRUNCATE` con otras conexiones abiertas puede esperar un bloqueo] → Gradle ejecuta los tests de integración uno tras otro; en el uso real, las suites llaman al reset antes de empezar.
- [Tokens emitidos antes de un reset apuntan a ids reutilizados] → Documentado en §7.6; las suites deben pedir tokens nuevos tras cada reset.
