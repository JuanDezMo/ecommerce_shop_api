# Tasks

> Convención (bootstrap D10): `*Test` corre con `./gradlew test` (sin Docker) y `*IT` con `./gradlew integrationTest` (Testcontainers sobre colima). Cada tarea sigue el ciclo test que falla → código mínimo → refactor. Gradle se ejecuta con Java 21 (`JAVA_HOME=$(/usr/libexec/java_home -v 21)`).

## 1. Entidad y repository

- [x] 1.1 Escribir `ProductRepositoryIT` (extiende `IntegrationTest`): `findAllByOrderByIdAsc()` devuelve 6 productos con ids 1 a 6 en orden, y `findById(1L)` trae `sku` `SL-HEAD-001`, `price` `59.99` (escala 2), `stock` 10, `imageKey` `headphones` y un `createdAt` no nulo. Verlo fallar (no compila). Crear `Product` según design D2 (solo getters, constructor `protected` vacío y constructor público sin `id`) y `ProductRepository` según D3. Verificar que `./gradlew integrationTest --tests ProductRepositoryIT` pasa y que `ShopApplicationIT` sigue pasando (`ddl-auto: validate` acepta el mapeo).

## 2. DTO y mapper

- [x] 2.1 Escribir `ProductMapperTest`: un `Product` construido con datos conocidos se convierte en un `ProductResponse` con los 7 campos iguales, y `price` conserva la escala (`25.00`). Además, el JSON de ese DTO serializado con un `ObjectMapper` cumple `product-response` según `ContractAssertions`. Verlo fallar. Crear `dto/ProductResponse` (record) y `ProductMapper` (`@Component`, sin dependencias). Verificar con `./gradlew test --tests ProductMapperTest`.

## 3. Service

- [x] 3.1 Escribir `ProductServiceImplTest` (Mockito, con `ProductMapper` real): `findAll()` devuelve los DTOs en el orden del repository y una lista vacía si no hay productos; `findById(1L)` devuelve el DTO; `findById(99L)` lanza `BusinessException` con `ErrorCode.PRODUCT_NOT_FOUND` y mensaje `Product 99 not found`. Verlo fallar. Crear `ProductService` (interfaz) y `ProductServiceImpl` (`@Service`, `@Transactional(readOnly = true)` a nivel de clase, inyección por constructor). Verificar con `./gradlew test --tests ProductServiceImplTest`.

## 4. Controller

- [x] 4.1 Escribir `ProductControllerTest` (`@WebMvcTest(ProductController.class)`, `@Import({SecurityConfig.class, GlobalExceptionHandler.class})`, `@MockitoBean ProductService`) con los escenarios felices: `GET /api/v1/products` responde 200 con el array en orden y cumple `product-list`; `GET /api/v1/products/1` responde 200 con los 7 campos y cumple `product-response`; el texto del JSON de un producto con precio `25.00` contiene `"price":25.00` (design D4). Verlo fallar. Crear `ProductController` según design D1. Verificar con `./gradlew test --tests ProductControllerTest`.
- [x] 4.2 Añadir a `ProductControllerTest` los escenarios de error: el service lanza `PRODUCT_NOT_FOUND` para 99 → 404 con `message` `Product 99 not found` y `fieldErrors` `[]`; `/api/v1/products/abc` y `/api/v1/products/1.5` → 400 con `fieldErrors` `[{"field":"id","message":"must be an integer"}]` sin llamar al service; `DELETE /api/v1/products/1` y `POST /api/v1/products` → 405 `METHOD_NOT_ALLOWED`. Cada respuesta se valida con `error-response`. Verificar que pasan (si alguno falla, ajustar el controller, no el handler).

## 5. Reglas de arquitectura

- [x] 5.1 Añadir a `ArchitectureTest` las tres reglas de design D6 con `allowEmptyShould(false)`. Comprobar que detectan errores: quitar temporalmente `@Transactional` de `ProductServiceImpl` y verificar que la regla falla con un mensaje que nombra la clase; restaurarlo. Verificar que `./gradlew test --tests ArchitectureTest` pasa.

## 6. Integración de punta a punta

- [x] 6.1 Escribir `ProductCatalogIT` (extiende `IntegrationTest`, usa `TestRestTemplate` sin cabecera `Authorization`) con cada escenario de `specs/product-catalog`: listado de 6 productos en orden; detalle exacto del producto 1; `price` de producto 4 igual a `25.00` en el texto del JSON; `stock` 0 del producto 3 y 1 del producto 5; ningún campo fuera de los 7; 404 para 99, 0 y -1 con el id en el `message`; 400 para `abc`; 405 para `DELETE /api/v1/products/1` y que después `GET /api/v1/products/1` sigue respondiendo 200. Cada respuesta se valida con su contrato. Verificar que `./gradlew integrationTest --tests ProductCatalogIT` pasa.
- [x] 6.2 Ampliar `OpenApiIT` para comprobar que `/v3/api-docs` incluye las rutas `/api/v1/products` y `/api/v1/products/{id}`. Verificar que pasa.
- [x] 6.3 Ejecutar `./gradlew check` y verificar que todos los tests (unitarios, `@WebMvcTest`, ArchUnit e integración) pasan.

## Workflow follow-up

- Revisión del usuario y commit (los hace el usuario).
- Archivar el change con `/opsx:archive product-catalog` para crear `openspec/specs/product-catalog/`.
