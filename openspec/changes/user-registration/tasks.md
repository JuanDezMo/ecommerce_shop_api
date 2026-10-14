# Tasks

> Convención (bootstrap D10): `*Test` corre con `./gradlew test` (sin Docker) y `*IT` con `./gradlew integrationTest` (Testcontainers sobre colima). Cada tarea sigue el ciclo test que falla → código mínimo → refactor. Gradle se ejecuta con Java 21 (`JAVA_HOME=$(/usr/libexec/java_home -v 21)`). Cada `@WebMvcTest` nombra su controller.

## 1. Entidad y repository

- [x] 1.1 Escribir `UserRepositoryIT` (extiende `IntegrationTest`): `existsByEmail("standard@shoplab.dev")` es `true` y `existsByEmail("nobody@registration.it")` es `false`; `findById(1L)` mapea `email`, `firstName`, `birthDate` `1990-05-20`, `country` `CO`, `plan` `BASIC`, `experienceLevel` 3, `newsletter` `false`, `phone` `null`, `status` `ACTIVE` y fechas no nulas; `findById(2L)` tiene `status` `LOCKED`. Verlo fallar. Crear `Plan`, `UserStatus`, `User` (design D4) y `UserRepository`. Verificar que `UserRepositoryIT` y `ShopApplicationIT` pasan (`ddl-auto: validate` acepta el mapeo).

## 2. DTOs y mapper

- [x] 2.1 Escribir `RegisterUserRequestTest`: `"  Ana.Gomez@Example.COM "` se normaliza a `ana.gomez@example.com`; `email` `null` sigue siendo `null`; `newsletter` `null` pasa a `false` y `true` se mantiene. Verlo fallar. Crear `dto/RegisterUserRequest` según design D2 y §9.2 (mensajes explícitos). Verificar con `./gradlew test --tests RegisterUserRequestTest`.
- [x] 2.2 Escribir `UserMapperTest`: un `User` con datos conocidos se convierte en un `UserResponse` con los 13 campos; `phone` `null` se mantiene; el JSON serializado con el `ObjectMapper` de Spring (fechas ISO, no números) cumple `user-response` y no contiene `password` ni `passwordHash`. Verlo fallar. Crear `dto/UserResponse` y `UserMapper`. Verificar con `./gradlew test --tests UserMapperTest`.

## 3. Service

- [x] 3.1 Escribir `UserServiceImplTest` (Mockito para `UserRepository`, `Clock.fixed(2026-10-11T12:00:00Z)`, `BCryptPasswordEncoder` y `UserMapper` reales) con el registro exitoso: devuelve `ACTIVE`, el email ya normalizado, `createdAt` y `termsAcceptedAt` iguales al instante del reloj, y el `User` guardado (capturado con `ArgumentCaptor`) tiene un hash que valida la contraseña y no es la contraseña. Verlo fallar. Crear `UserService` y `UserServiceImpl` (design D3) con el camino feliz. Verificar con `./gradlew test --tests UserServiceImplTest`.
- [x] 3.2 Añadir a `UserServiceImplTest` las reglas de negocio: cumple 18 hoy → OK; cumple 18 mañana → `UNDERAGE_USER`; nacido el 29-02-2008 con reloj en 2026-02-28 → `UNDERAGE_USER`; `termsAccepted` `false` → `TERMS_NOT_ACCEPTED`; `existsByEmail` `true` → `EMAIL_ALREADY_EXISTS`; `saveAndFlush` lanza `DataIntegrityViolationException` → `EMAIL_ALREADY_EXISTS`; orden: menor + sin términos + email repetido → `UNDERAGE_USER`, y adulto + sin términos + email repetido → `TERMS_NOT_ACCEPTED`. En cada error se verifica que no se llamó a `saveAndFlush` (salvo en la carrera). Verlos fallar. Completar `UserServiceImpl`. Verificar que pasan.

## 4. Controller

- [x] 4.1 Escribir `UserControllerTest` (`@WebMvcTest(UserController.class)`, `@Import({SecurityConfig.class, GlobalExceptionHandler.class})`, `@MockitoBean UserService`) con el camino feliz: un body válido responde 201, el JSON cumple `user-response`, y el service recibe el email ya normalizado y `newsletter` `false` cuando no se envía. Verlo fallar. Crear `UserController` según design D1. Verificar con `./gradlew test --tests UserControllerTest`.
- [x] 4.2 Añadir a `UserControllerTest` los escenarios de *Validación de los campos* de la spec (tests parametrizados por campo y valor, con el `fieldError` exacto) más: `{}` responde los 9 errores ordenados; `password` `abc` responde sus dos errores ordenados; `country` `""` responde sus dos errores; `"plan": "GOLD"`, `"experienceLevel": "3"`, `"termsAccepted": "true"` y `"birthDate": "20-05-1990"` responden su error de tipo; `GET /api/v1/users` responde 405. Cada respuesta se valida con `error-response` y se verifica que el service no fue llamado. Los errores de negocio (422 y 409) se prueban con el service mockeado lanzando la `BusinessException`. Verificar que pasan (si alguno falla, ajustar el DTO).

## 5. Integración de punta a punta

- [x] 5.1 Escribir `UserRegistrationIT` (extiende `IntegrationTest`, `TestRestTemplate` sin `Authorization`, emails `@registration.it` y limpieza en `@AfterEach` según design D6): alta completa → 201 con contrato y `status` `ACTIVE`; en la BD el `password_hash` verifica la contraseña con BCrypt; email con mayúsculas y espacios se guarda normalizado; registrar dos veces el mismo email → 409; `standard@shoplab.dev` y `Standard@ShopLab.dev` → 409; menor de edad → 422 `UNDERAGE_USER`; `termsAccepted` `false` → 422 `TERMS_NOT_ACCEPTED`; un 400 no crea el usuario; `GET /api/v1/users` → 405. Verificar que `./gradlew integrationTest --tests UserRegistrationIT` pasa y que `SeedDataIT` sigue pasando en la misma ejecución.
- [x] 5.2 Ampliar `OpenApiIT` para comprobar que `/v3/api-docs` incluye la ruta `/api/v1/users`. Verificar que pasa.
- [x] 5.3 Ejecutar `./gradlew clean check` y verificar que todos los tests pasan, incluida `ArchitectureTest` con `UserServiceImpl`.

## Workflow follow-up

- Revisión del usuario y commit (los hace el usuario).
- Archivar el change con `/opsx:archive user-registration` para crear `openspec/specs/user-registration/`.
