# Tasks

> Convención (bootstrap D10): `*Test` corre con `./gradlew test` (sin Docker) y `*IT` con `./gradlew integrationTest` (Testcontainers sobre colima). Cada tarea sigue el ciclo test que falla → código mínimo → refactor. Gradle se ejecuta con Java 21 (`JAVA_HOME=$(/usr/libexec/java_home -v 21)`). Cada `@WebMvcTest` nombra su controller e importa `WebMvcSecurityConfig`.

## 1. Persistencia

- [x] 1.1 Crear `support/SeedData.restoreOrdersAndStock(JdbcTemplate)` (design D8). Ampliar `ProductRepositoryIT`: `decreaseStock(1L, 2)` devuelve 1 y el stock queda en 8; `decreaseStock(5L, 2)` devuelve 0 y el stock sigue en 1; `decreaseStock(99L, 1)` devuelve 0; restaurar con `SeedData` en `@AfterEach`. Verlo fallar. Añadir `decreaseStock` a `ProductRepository` (design D3). Verificar que `ProductRepositoryIT`, `ProductCatalogIT` y `SeedDataIT` pasan en la misma ejecución.
- [x] 1.2 Escribir `OrderRepositoryIT`: `findAllByUserIdOrderByCreatedAtDescIdDesc(3L)` devuelve las órdenes 2 y 1 en ese orden, con sus ítems (`@OrderBy("id")`) y los `sku` de los productos; `findByIdAndUserId(1L, 3L)` la encuentra y `findByIdAndUserId(1L, 1L)` no; guardar una orden nueva del usuario 1 con dos `addItem` persiste los dos ítems con su `order_id` (comprobado con `JdbcTemplate`); `SeedData` en `@AfterEach`. Verlo fallar. Crear `OrderStatus`, `PaymentMethod`, `Order`, `OrderItem` (design D2) y `OrderRepository` (design D5). Verificar que pasa y que `ShopApplicationIT` sigue pasando (`ddl-auto: validate`).

## 2. DTOs y mapper

- [x] 2.1 Crear los DTOs de entrada `CreateOrderRequest`, `OrderItemRequest` y `ShippingRequest` (design D7) y los de salida `OrderResponse`, `OrderItemResponse` y `ShippingResponse`. Escribir `OrderMapperTest`: una orden con 2 ítems (59.99 × 2 y 25.00 × 1) produce `subtotal` `119.98` y `25.00`, `total` igual al de la entidad, ítems en orden con `sku`, `name` e `imageKey`, `shipping` copiado; el JSON (ObjectMapper configurado como Spring Boot) cumple `order-response`, contiene `"subtotal":25.00` y no contiene `userId`. Verlo fallar. Crear `OrderMapper` (design D6). Verificar con `./gradlew test --tests OrderMapperTest`.

## 3. Service

- [x] 3.1 Escribir `OrderServiceImplTest` (Mockito para los tres repositories, `OrderMapper` real, `Clock.fixed`) con el camino feliz: crea la orden `CONFIRMED` con `unitPrice` del producto (no del request), `total` `144.98`, `createdAt` del reloj, ítems en el orden del request, llama a `decreaseStock` por ítem y a `save` una vez; `findAllByUser(3L)` mapea lo del repository; `findByIdForUser(1L, 3L)` devuelve la orden. Verlo fallar. Crear `OrderService` y `OrderServiceImpl` (design D4). Verificar con `./gradlew test --tests OrderServiceImplTest`.
- [x] 3.2 Añadir a `OrderServiceImplTest` las reglas y su orden: `items` vacío → `EMPTY_ORDER`; `[1, 4, 1]` → `DUPLICATE_ORDER_ITEM` con `Product 1 appears…`; `[99, 99]` → `DUPLICATE_ORDER_ITEM`; usuario inexistente → `AUTH_TOKEN_INVALID`; producto 99 → `PRODUCT_NOT_FOUND`; `decreaseStock` devuelve 0 → `INSUFFICIENT_STOCK` con el id; `[3 sin stock, 99]` → `INSUFFICIENT_STOCK`; `[99, 3]` → `PRODUCT_NOT_FOUND`; `findByIdForUser(1L, 1L)` vacío → `ORDER_NOT_FOUND` con `Order 1 not found`; usuario inexistente en `findAllByUser` y `findByIdForUser` → `AUTH_TOKEN_INVALID` sin consultar `OrderRepository`. En cada rechazo se verifica que no se llamó a `save` y, en los que van antes del loop, tampoco a `decreaseStock`. Verlos fallar donde aplique y completar `OrderServiceImpl`. Verificar que pasan.

## 4. Controller

- [x] 4.1 Escribir `OrderControllerTest` (`@WebMvcTest(OrderController.class)`, `@Import(WebMvcSecurityConfig.class)`, `@MockitoBean OrderService`, tokens de `TestTokens`) con los caminos felices: `POST` válido → 201 con contrato `order-response` y el service recibe el `userId` del token; `GET /orders` → 200 con contrato `order-list`; `GET /orders/1` → 200. Verlo fallar. Crear `OrderController` (design D1). Verificar con `./gradlew test --tests OrderControllerTest`.
- [x] 4.2 Añadir a `OrderControllerTest` los escenarios de *Validación del body de la orden* de la spec (cantidad 0 y 100, `[null]`, `items` null, `shipping` inválido y ausente, `paymentMethod` `CRYPTO`, `quantity` `"2"` y `1.5`, `items: []` sin `shipping` → `VALIDATION_ERROR`), verificando que el service no se llama; los errores de negocio lanzados por el service (`EMPTY_ORDER`, `DUPLICATE_ORDER_ITEM`, `PRODUCT_NOT_FOUND`, `INSUFFICIENT_STOCK`, `AUTH_TOKEN_INVALID`, `ORDER_NOT_FOUND`) con su status y `message`; sin token → 401 `AUTH_TOKEN_MISSING`; `PUT /orders` con token → 405; `GET /orders/abc` → 400. Cada error se valida con `error-response`. Verificar que pasan (si alguno falla, ajustar los DTOs).

## 5. Integración de punta a punta

- [x] 5.1 Escribir `OrdersIT` (extiende `IntegrationTest`, tokens del login real, `SeedData.restoreOrdersAndStock` en `@AfterEach`) con los escenarios de la spec: creación con contrato, totales y sin `userId`; stock 8 tras comprar 2 del producto 1; precios del cliente ignorados; 409 para los productos 3 y 5; rollback (producto 1 sigue en 10); 404 para el producto 99; `EMPTY_ORDER`; `DUPLICATE_ORDER_ITEM`; usuario registrado y borrado → 401 `AUTH_TOKEN_INVALID` en `POST` (sin descontar stock), en `GET /orders` y en `GET /orders/1`; historial de `buyer` (2 y 1, con subtotales); `standard` sin órdenes → `[]`; orden nueva aparece primera; orden de otro usuario → 404; `999` → 404; sin token → 401; `PUT` con token → 405. Verificar con `./gradlew integrationTest --tests OrdersIT`.
- [x] 5.2 Añadir a `OrdersIT` la compra simultánea de la última unidad (design D9, riesgo de concurrencia): dos hilos con `CountDownLatch` piden 1 unidad del producto 5; exactamente una respuesta 201 y una 409, y el stock queda en 0. Ejecutarlo 5 veces seguidas (`--rerun-tasks`) para confirmar que es estable.
- [x] 5.3 Ampliar `OpenApiIT` con las rutas `/api/v1/orders` y `/api/v1/orders/{id}` con `bearerAuth`. Ejecutar `./gradlew clean check` y verificar que todos los tests pasan (incluidos `ProductCatalogIT` y `SeedDataIT`, que dependen de que la limpieza funcione, y `ArchitectureTest` con `OrderServiceImpl`).

## Workflow follow-up

- Revisión del usuario y commit (los hace el usuario).
- Archivar el change con `/opsx:archive orders` para crear `openspec/specs/orders/`.
