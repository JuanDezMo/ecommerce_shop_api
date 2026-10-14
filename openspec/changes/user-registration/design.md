# Design

## Context

Ya existen la tabla `users` (V1) con `UNIQUE (email)` y `CHECK` para `plan`, `status` y `experience_level`; los 3 usuarios semilla (V4); `ValidationPatterns` (`PASSWORD`, `COUNTRY`); el bean `Clock` en UTC; el bean `PasswordEncoder` (BCrypt) en el `SecurityConfig` provisional; los `ErrorCode` `UNDERAGE_USER`, `TERMS_NOT_ACCEPTED` y `EMAIL_ALREADY_EXISTS`; el contrato `user-response.schema.json`; y un `GlobalExceptionHandler` que ya ordena los `fieldErrors` y traduce los errores de tipo (enum `plan`, booleano `termsAccepted`, fecha `birthDate`, entero `experienceLevel`).

`product-catalog` dejó la plantilla del paquete y de los tests por capa. Ver `proposal.md` para la motivación y `specs/user-registration/spec.md` para el comportamiento.

## Goals / Non-Goals

**Goals:**
- Que el service sea la única pieza con reglas de negocio y que se pueda probar con un reloj fijo (casos de cumpleaños de §6.5).
- Dejar `User`, `UserRepository` y `UserMapper` listos para que `auth-login` y `auth-jwt-filter` los reutilicen sin cambiarlos.

**Non-Goals:**
- `GET /users/me` y cualquier uso de JWT (`auth-jwt-filter`).
- `findByEmail` para el login (lo añade `auth-login`, que es quien lo usa).
- Recortar espacios de `firstName` / `lastName`: §6.4 no lo pide; se guardan tal cual llegan.

## Decisions

### D1. Las clases del paquete `user`

```
user/
├── User.java                     ← @Entity
├── UserStatus.java               ← enum ACTIVE, LOCKED
├── Plan.java                     ← enum BASIC, PRO
├── UserRepository.java           ← existsByEmail(String)
├── UserService.java              ← interfaz: UserResponse register(RegisterUserRequest)
├── UserServiceImpl.java          ← @Service @Transactional; reglas de negocio
├── UserMapper.java               ← @Component: User → UserResponse
├── UserController.java           ← POST /api/v1/users → 201
└── dto/
    ├── RegisterUserRequest.java  ← record con @Valid (§9.2), normaliza email y newsletter
    └── UserResponse.java         ← record con los 13 campos del contrato
```

Mismo reparto que `product-catalog`. `UserController.register` devuelve `ResponseEntity.status(HttpStatus.CREATED).body(...)`, sin cabecera `Location`: no existe `GET /users/{id}` al que apuntar.

### D2. `RegisterUserRequest`: validación y normalización

- Se copia el record de §9.2 del documento, con cada `message` escrito a mano (no depende del idioma del sistema).
- El *compact constructor* normaliza `email` (`trim` + `toLowerCase(Locale.ROOT)`) y convierte `newsletter` `null` en `false`. Se ejecuta al deserializar, **antes** de `@Valid`, así `" Ana@Example.com "` es válido (§6.3).
- `experienceLevel` es `Integer` y `termsAccepted` es `Boolean` (no primitivos): con primitivos, un campo ausente llegaría como `0` / `false` y `@NotNull` nunca saltaría.
- `phone` no lleva `@NotBlank`: `null` es válido (`@Pattern` ignora `null`), pero `""` no cumple `^\d{7,15}$` y responde 400. *Alternativa descartada:* convertir `""` en `null`; §6.4 no lo pide y la app puede no enviar el campo.

### D3. Reglas de negocio en `UserServiceImpl.register`

```java
LocalDate today = LocalDate.now(clock);
if (Period.between(request.birthDate(), today).getYears() < 18) → UNDERAGE_USER
if (!request.termsAccepted())                                   → TERMS_NOT_ACCEPTED
if (userRepository.existsByEmail(request.email()))              → EMAIL_ALREADY_EXISTS
User user = ... (passwordEncoder.encode, status ACTIVE, now = Instant.now(clock))
try { saveAndFlush } catch (DataIntegrityViolationException e) → EMAIL_ALREADY_EXISTS
return userMapper.toResponse(saved);
```

- El orden es el de §6.4 y el de la spec *Orden de evaluación*.
- **Carrera de dos registros simultáneos**: `saveAndFlush` hace que el `INSERT` ocurra dentro del `try`; la única `UNIQUE` de `users` es `email`, así que el `catch` no puede tener otra causa (§6.4).
- `Clock` y `PasswordEncoder` se inyectan por constructor. Los tests unitarios usan `Clock.fixed(...)` y un `BCryptPasswordEncoder` real (con un mock no se podría verificar que el hash valida la contraseña).
- `termsAcceptedAt` y `createdAt` usan el **mismo** `Instant` (una sola llamada a `Instant.now(clock)`), truncado a microsegundos (`truncatedTo(ChronoUnit.MICROS)`): PostgreSQL guarda microsegundos, así lo que devuelve el `POST` coincide con lo que devolverá después `GET /users/me`. *Alternativa descartada:* truncar a segundos, que pierde precisión sin necesidad.

### D4. Entidad `User`

- Campos de la tabla con `@Enumerated(EnumType.STRING)` para `plan` y `status` (§11 regla 7), `Instant` para las fechas y `LocalDate` para `birthDate`.
- `experience_level` es `smallint` en V1. En Java se mantiene `Integer` (igual que en el DTO y el mapper) con `@JdbcTypeCode(SqlTypes.SMALLINT)`; sin esa anotación `ddl-auto: validate` falla al arrancar (*found int2, expecting integer*). *Alternativa descartada:* usar `Short` en la entidad, que obliga a convertir en el service y en los tests.
- Constructor vacío `protected` y **setters** para que el service construya el usuario campo a campo (como el ejemplo de §6.5). `UserServiceImpl` está en el mismo paquete, así que puede usar `new User()`; ninguna otra feature puede crear usuarios. *Alternativa descartada:* un constructor con 13 parámetros, donde es fácil intercambiar dos `String` (`firstName` / `lastName`) sin que el compilador lo note.
- Sin `equals`, `hashCode` ni `toString` (§11 regla 10). Así el hash tampoco aparece en logs por accidente.

### D5. Tests por capa

| Test | Tipo | Qué verifica |
|---|---|---|
| `RegisterUserRequestTest` | Unitario | Normalización de email y `newsletter` en el constructor del record |
| `UserMapperTest` | Unitario | Los 13 campos; el JSON no tiene `password` ni `passwordHash` y cumple `user-response` |
| `UserServiceImplTest` | Unitario (Mockito, `Clock.fixed`) | Casos de cumpleaños de §6.5, términos, email duplicado, carrera con `DataIntegrityViolationException`, orden de las reglas, hash BCrypt, `ACTIVE`, fechas del reloj |
| `UserControllerTest` | `@WebMvcTest(UserController.class)` + `@MockitoBean UserService` | 201 y contrato; cada validación de §6.4 (tests parametrizados); `{}` con los 9 errores ordenados; errores de tipo; el service no se llama si la validación falla; 405 para `GET` |
| `UserRegistrationIT` | `IntegrationTest` | Flujo completo: 201, hash en la BD, normalización, 409 contra un usuario semilla y contra uno recién creado, 422, 405, sin token |

### D6. Limpieza de los tests de integración

`SeedDataIT` exige exactamente 3 usuarios y todos los `*IT` comparten el mismo PostgreSQL. Por eso `UserRegistrationIT` usa emails con un dominio propio (`@registration.it`) y un `@AfterEach` que ejecuta `DELETE FROM users WHERE email LIKE '%@registration.it'` con `JdbcTemplate`. *Alternativa descartada:* cambiar `SeedDataIT` para filtrar por los emails semilla, que debilita la comprobación de "exactamente 3 usuarios". Cuando exista `/test/reset` (`testing-profile`) podrá revisarse.

## Risks / Trade-offs

- [`@Past` usa el reloj del sistema, no el bean `Clock`] → En el contenedor ambos están en UTC (§6.5). Los tests de `@Past` usan fechas claramente futuras (`2999-01-01`) y no la frontera exacta de hoy.
- [Los tests de cumpleaños dependen de la fecha] → Se prueban solo en `UserServiceImplTest` con `Clock.fixed`; el IT usa fechas lejanas (`1990-05-20` y un menor nacido hace 10 años calculado con `LocalDate.now()`).
- [BCrypt es lento a propósito (~100 ms por hash)] → Aceptable: pocos tests lo usan con el encoder real.
- [`auth-jwt-filter` debe dejar `/api/v1/users` en `permitAll` como ruta exacta] → `UserRegistrationIT` registra sin cabecera `Authorization` y fallará si la ruta deja de ser pública.
