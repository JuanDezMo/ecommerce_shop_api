# Design

## Context

Ya existen `User` (con `passwordHash` y `status`), `UserRepository`, el bean `PasswordEncoder` (BCrypt), el bean `Clock` (UTC), los `ErrorCode` `INVALID_CREDENTIALS` y `USER_LOCKED`, el contrato `login-response.schema.json` y las dependencias `jjwt-api` / `jjwt-impl` / `jjwt-jackson` 0.12.7. `docker-compose.yml` ya pasa `JWT_SECRET` a la API y `.env.example` lo documenta. El `SecurityConfig` provisional deja todas las rutas públicas.

Hay dos arranques de Spring en los tests de integración: `support/IntegrationTest` (todos los `*IT` salvo uno) y `HealthDatabaseDownIT`, que declara su propio `@SpringBootTest`.

Ver `proposal.md` para la motivación y `specs/user-login/spec.md` para el comportamiento.

## Goals / Non-Goals

**Goals:**
- Emitir el token y aislar jjwt en una sola clase (`JjwtService`), detrás de una interfaz que `auth-jwt-filter` ampliará con `parse`.
- Que una configuración incorrecta del secreto se detecte al arrancar, no en el primer login.

**Non-Goals:**
- Validar tokens, `InvalidTokenException`, el filtro, el entry point y `AuthUser` (`auth-jwt-filter`).
- Refresh token y logout (fase 2, §1).
- Igualar el tiempo de respuesta entre "email inexistente" y "contraseña incorrecta": ver *Risks*.

## Decisions

### D1. Clases nuevas

```
auth/
├── AuthController.java          ← POST /api/v1/auth/login → 200
├── AuthService.java             ← interfaz: LoginResponse login(LoginRequest)
├── AuthServiceImpl.java         ← @Service @Transactional(readOnly = true)
└── dto/
    ├── LoginRequest.java        ← record: email (normalizado), password; solo @NotBlank
    └── LoginResponse.java       ← record: accessToken, tokenType, expiresIn
security/
├── JwtProperties.java           ← record @ConfigurationProperties("jwt"): secret, ttl; valida el secreto
├── JwtService.java              ← interfaz: String generateToken(Long userId, String email)
└── JjwtService.java             ← @Service: implementación con jjwt (única clase que importa io.jsonwebtoken)
```

- `AuthServiceImpl` usa `UserRepository` directamente (concesión documentada en §3.3), no `UserService`: así `UserService` no expone el hash. `UserRepository` gana `Optional<User> findByEmail(String email)`.
- `JwtService.generateToken` recibe `userId` y `email`, **no** un `User`: el paquete `security` no depende del paquete `user`.
- `LoginResponse.expiresIn` lo calcula `AuthServiceImpl` con `jwtProperties.ttl().toSeconds()`; `tokenType` es siempre `"Bearer"`.

### D2. Reglas del login (§7.2 y §8.3)

```java
User user = userRepository.findByEmail(request.email())
        .filter(u -> hasValidPasswordFormat(request.password()))        // ≤ 20 y ASCII visible
        .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
        .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));
if (user.getStatus() == UserStatus.LOCKED) → USER_LOCKED
return new LoginResponse(jwtService.generateToken(user.getId(), user.getEmail()), "Bearer", ttlSeconds);
```

- El filtro de formato evita pasarle a BCrypt una contraseña de más de 72 bytes: Spring Security 6.3+ lanza una excepción con esas contraseñas, y terminaría en 500. Se usa una constante `MAX_PASSWORD_LENGTH = 20` y la regex `[\x21-\x7E]+`, las mismas fronteras del registro.
- `LoginRequest` solo usa `@NotBlank`: **no** aplica `@Email` ni la política de contraseña, para que cualquier credencial mala sea 401 (§7.2).
- El email se normaliza en el *compact constructor*, igual que en `RegisterUserRequest`.

### D3. `JwtProperties` con fail fast

```java
@ConfigurationProperties("jwt")
public record JwtProperties(String secret, Duration ttl) {
    public JwtProperties {
        if (secret == null || secret.isBlank()) throw new IllegalStateException("jwt.secret (JWT_SECRET) is required");
        if (secret.getBytes(UTF_8).length < 32) throw new IllegalStateException("jwt.secret must be at least 32 bytes");
        if (ttl == null) ttl = Duration.ofHours(1);
    }
}
```

- La validación vive en el *compact constructor*: Spring lo llama al enlazar la configuración y el error aborta el arranque. *Alternativa descartada:* `@Validated` con `@NotBlank` + `@Size`, porque `@Size` cuenta caracteres y no bytes, y un junior tendría que leer dos mecanismos.
- Se registra con `@ConfigurationPropertiesScan` en `ShopApplication`.
- `application.yml`: `jwt.secret: ${JWT_SECRET:}` (vacío si falta → error claro de "is required") y `jwt.ttl: 1h`. `application-testing.yml`: `jwt.ttl: 15m`.
- 32 bytes es el mínimo que jjwt acepta para HS256; con menos, `Keys.hmacShaKeyFor` fallaría con un mensaje menos claro.

### D4. `JjwtService`

- Construye la clave una sola vez en el constructor: `Keys.hmacShaKeyFor(secret.getBytes(UTF_8))`. Con 32 o más bytes, `signWith(key)` usa HS256... salvo que el secreto mida 48 o 64 bytes o más, en cuyo caso jjwt elegiría HS384/HS512. Para que el algoritmo sea **siempre** HS256 (§8.4) se firma con `signWith(key, Jwts.SIG.HS256)`.
- `iat = Instant.now(clock)` y `exp = iat + ttl`. El `Clock` inyectado permite que `testing-profile` genere tokens ya expirados con un reloj en el pasado (§16), y que los tests fijen `iat`.
- Claims: `subject(userId.toString())`, `claim("email", email)`, `issuedAt`, `expiration`. Nada más.
- `jjwt-impl` es `runtimeOnly`: el código solo usa `io.jsonwebtoken.Jwts`, `Keys` y `Claims` de `jjwt-api`.

### D5. Secreto en los tests

- Una constante `support/TestJwt.SECRET` (40 bytes ASCII) se pasa como `properties = "jwt.secret=" + TestJwt.SECRET` en `IntegrationTest` y en `HealthDatabaseDownIT`. *Alternativa descartada:* un `src/test/resources/application.yml`, que **sustituiría** al de `src/main` en el classpath y dejaría a los tests sin la configuración real (Flyway, Jackson estricto, Actuator).
- Los `@WebMvcTest` no necesitan secreto: no cargan `JjwtService` (`AuthControllerTest` mockea `AuthService`).
- El fail fast se prueba con `ApplicationContextRunner` (sin Docker): un contexto con `JwtProperties` falla sin secreto y con 31 bytes, y arranca con 32.

### D6. Tests por capa

| Test | Tipo | Qué verifica |
|---|---|---|
| `LoginRequestTest` | Unitario | Normalización del email |
| `JwtPropertiesTest` | Unitario + `ApplicationContextRunner` | Secreto ausente, en blanco, de 31 bytes y de 32 bytes; `ttl` por defecto |
| `JjwtServiceTest` | Unitario (reloj fijo) | Cabecera `HS256`, claims `sub`, `email`, `iat`, `exp = iat + ttl`; con un secreto de 64 bytes sigue siendo HS256; el token se verifica con la misma clave y falla con otra |
| `AuthServiceImplTest` | Unitario (Mockito, BCrypt real) | Login OK; email inexistente; contraseña incorrecta; contraseña de 100 caracteres, con espacio o con `ñ` → 401 sin llamar a `matches`; `LOCKED` con contraseña correcta → 423; `LOCKED` con contraseña incorrecta → 401 |
| `AuthControllerTest` | `@WebMvcTest(AuthController.class)` | 200 y contrato; `{}` y contraseña en blanco → 400; `not-an-email` llega al service (no es 400); 401 y 423 desde el service; `GET` → 405 |
| `AuthLoginIT` | `IntegrationTest` | Login de `standard` (claims decodificados, `expiresIn` 3600), `locked` → 423, contraseña mala → 401, email inexistente → 401, registro + login del mismo usuario (limpieza `@registration.it`), sin token |
| `AuthLoginTestingProfileIT` | `IntegrationTest` + `@ActiveProfiles("testing")` | `expiresIn` es 900 |

## Risks / Trade-offs

- [Un email inexistente responde algo más rápido que una contraseña incorrecta, porque no se ejecuta BCrypt] → Aceptado para el MVP: el `code` y el `message` son idénticos, que es lo que §8.3 exige y lo que verifican las pruebas. Igualar tiempos (hash "señuelo") añade complejidad poco clara para un junior.
- [Arrancar desde el IDE sin `JWT_SECRET` ahora falla] → Es el comportamiento pedido (§8.4). El mensaje nombra `jwt.secret (JWT_SECRET)`; `.env.example` ya trae un valor de ejemplo.
- [Un usuario que pasa a `LOCKED` conserva su token hasta que expira] → Limitación conocida de §8.4; no aplica todavía porque aún no se validan tokens.
- [El login de los IT usa BCrypt real (~100 ms)] → Pocos casos; aceptable.
