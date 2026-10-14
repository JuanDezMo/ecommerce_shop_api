# Tasks

> Convención (bootstrap D10): `*Test` corre con `./gradlew test` (sin Docker) y `*IT` con `./gradlew integrationTest` (Testcontainers sobre colima). Cada tarea sigue el ciclo test que falla → código mínimo → refactor. Gradle se ejecuta con Java 21 (`JAVA_HOME=$(/usr/libexec/java_home -v 21)`). Cada `@WebMvcTest` nombra su controller.

## 1. Configuración del secreto y del TTL

- [x] 1.1 Escribir `JwtPropertiesTest`: el constructor falla con secreto `null`, en blanco y de 31 bytes (mensajes de design D3), acepta 32 bytes y usa 1 h si `ttl` es `null`; con `ApplicationContextRunner` (`withUserConfiguration` de una clase con `@EnableConfigurationProperties(JwtProperties.class)`), el contexto falla sin `jwt.secret` y arranca con `jwt.secret` de 32 bytes y `jwt.ttl=15m`. Verlo fallar. Crear `security/JwtProperties` y añadir `@ConfigurationPropertiesScan` a `ShopApplication`. Verificar con `./gradlew test --tests JwtPropertiesTest`.
- [x] 1.2 Crear `support/TestJwt.SECRET` y pasarlo como `properties` en `IntegrationTest` y `HealthDatabaseDownIT` (design D5). Añadir a `application.yml` `jwt.secret: ${JWT_SECRET:}` y `jwt.ttl: 1h`, y a `application-testing.yml` `jwt.ttl: 15m`. Verificar que `./gradlew integrationTest` sigue pasando completo (los IT arrancan con el secreto de prueba).

## 2. Emisión del token

- [x] 2.1 Escribir `JjwtServiceTest` (reloj fijo `2026-10-11T12:00:00Z`, `ttl` 1 h): el token tiene 3 partes; parseado con `Jwts.parser().verifyWith(misma clave)` tiene cabecera `alg` `HS256`, `sub` `"1"`, `email`, `iat` igual al reloj y `exp` una hora después, y ningún otro claim; con un secreto de 64 bytes sigue siendo `HS256`; con otra clave la verificación falla. Verlo fallar. Crear `security/JwtService` (solo `generateToken`) y `security/JjwtService` (design D4). Verificar con `./gradlew test --tests JjwtServiceTest`.

## 3. DTOs y service

- [x] 3.1 Escribir `LoginRequestTest`: `"  Standard@ShopLab.dev "` se normaliza a `standard@shoplab.dev` y `null` sigue siendo `null`. Verlo fallar. Crear `auth/dto/LoginRequest` (solo `@NotBlank` con `must not be blank`) y `auth/dto/LoginResponse`. Verificar con `./gradlew test --tests LoginRequestTest`.
- [x] 3.2 Escribir `AuthServiceImplTest` (Mockito para `UserRepository` y `JwtService`, `BCryptPasswordEncoder` real, `JwtProperties` con `ttl` 1 h) con el login exitoso: devuelve el token del `JwtService` (llamado con el id y el email del usuario), `tokenType` `Bearer` y `expiresIn` 3600. Verlo fallar. Añadir `findByEmail` a `UserRepository`, crear `auth/AuthService` y `auth/AuthServiceImpl` (design D2). Verificar con `./gradlew test --tests AuthServiceImplTest`.
- [x] 3.3 Añadir a `AuthServiceImplTest` los rechazos: email inexistente → `INVALID_CREDENTIALS`; contraseña incorrecta → `INVALID_CREDENTIALS`; contraseña de 100 caracteres, `Secret 123` y `Señal_123` → `INVALID_CREDENTIALS` sin que BCrypt reciba la contraseña (usar un `PasswordEncoder` espía con `Mockito.spy`); usuario `LOCKED` con contraseña correcta → `USER_LOCKED`; `LOCKED` con contraseña incorrecta → `INVALID_CREDENTIALS`. En todos los rechazos se verifica que no se generó token. Verlos fallar donde aplique y completar `AuthServiceImpl`. Verificar que pasan.

## 4. Controller

- [x] 4.1 Escribir `AuthControllerTest` (`@WebMvcTest(AuthController.class)`, `@Import({SecurityConfig.class, GlobalExceptionHandler.class})`, `@MockitoBean AuthService`): credenciales válidas → 200 con contrato `login-response`; el service recibe el email normalizado; `{}` → 400 con los dos `fieldErrors` ordenados; contraseña `"   "` → 400 solo para `password`; `not-an-email` sí llega al service; el service lanza `INVALID_CREDENTIALS` → 401 y `USER_LOCKED` → 423 con su `message`; `GET /api/v1/auth/login` → 405. Las respuestas de error se validan con `error-response`. Verlo fallar. Crear `auth/AuthController`. Verificar con `./gradlew test --tests AuthControllerTest`.

## 5. Integración de punta a punta

- [x] 5.1 Escribir `AuthLoginIT` (extiende `IntegrationTest`, sin `Authorization`): `standard@shoplab.dev` / `Secret_123` → 200 con contrato y `expiresIn` 3600; el token, verificado con `TestJwt.SECRET`, tiene `sub` `"1"`, `email` `standard@shoplab.dev` y `exp - iat` 3600; email con mayúsculas y espacios → 200; `locked@shoplab.dev` → 423; `locked` con contraseña mala → 401; contraseña mala, email inexistente, contraseña de 100 caracteres y `not-an-email` → 401 con el mismo body salvo `timestamp`; `{}` → 400; `GET` → 405; registrar un usuario `@registration.it` y hacer login con él → 200 (limpieza en `@AfterEach`). Verificar con `./gradlew integrationTest --tests AuthLoginIT`.
- [x] 5.2 Escribir `AuthLoginTestingProfileIT` (`@ActiveProfiles("testing")`): el login de `standard` devuelve `expiresIn` 900. Ampliar `OpenApiIT` para comprobar la ruta `/api/v1/auth/login`. Verificar que pasan.
- [x] 5.3 Ejecutar `./gradlew clean check` y verificar que todos los tests pasan, incluida `ArchitectureTest` con `AuthServiceImpl`.

## Workflow follow-up

- Revisión del usuario y commit (los hace el usuario).
- Archivar el change con `/opsx:archive auth-login` para crear `openspec/specs/user-login/`.
