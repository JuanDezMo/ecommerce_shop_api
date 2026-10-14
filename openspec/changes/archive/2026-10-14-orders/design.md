# Design

## Context

- Ya existen las tablas `orders` y `order_items` (V3) con sus `CHECK` y claves foráneas, las 2 órdenes semilla de `buyer` (ids 1 y 2) y la tabla `products` con `CHECK (stock >= 0)`.
- `Product` es una entidad de solo lectura; `ProductRepository` y `UserRepository` existen; el `Clock` en UTC y la seguridad JWT (`AuthUser`, `WebMvcSecurityConfig`, `TestTokens`) están listos.
- `ErrorCode` ya tiene `EMPTY_ORDER`, `DUPLICATE_ORDER_ITEM`, `PRODUCT_NOT_FOUND`, `INSUFFICIENT_STOCK`, `ORDER_NOT_FOUND` y `AUTH_TOKEN_INVALID`. Los contratos `order-response` y `order-list` existen. `ValidationPatterns` tiene `POSTAL_CODE` y `COUNTRY`.
- §7.4, §10 y §11 de `docs/shop-backend-arquitectura.md` fijan las reglas, el flujo y las diez reglas de JPA que este change aplica por primera vez con relaciones.
- `ProductCatalogIT` y `SeedDataIT` comprueban el stock y las órdenes de la semilla, y todos los `*IT` comparten el mismo PostgreSQL.

Ver `proposal.md` para la motivación y `specs/orders/spec.md` para el comportamiento.

## Goals / Non-Goals

**Goals:**
- Que el descuento de stock sea atómico y no permita sobreventa con compras simultáneas.
- Que la plantilla de JPA con relaciones (`@OneToMany` / `@ManyToOne` / `@EntityGraph`) quede escrita una vez, siguiendo §11 al pie de la letra, para que un junior la copie.

**Non-Goals:**
- Cancelar, pagar o cambiar el estado de una orden (solo existe `CONFIRMED`).
- Paginación o filtros del historial (§7.3).
- `/test/reset` (`testing-profile`).

## Decisions

### D1. Clases del paquete `order`

```
order/
├── Order.java, OrderItem.java            ← entidades (§11.3)
├── OrderStatus.java                       ← enum CONFIRMED
├── PaymentMethod.java                     ← enum CARD, CASH, TRANSFER
├── OrderRepository.java                   ← consultas con @EntityGraph
├── OrderService.java / OrderServiceImpl   ← create, findAllByUser, findByIdForUser (§5)
├── OrderMapper.java                       ← Order → OrderResponse (calcula subtotal)
├── OrderController.java                   ← POST/GET /api/v1/orders, GET /{id}
└── dto/
    ├── CreateOrderRequest.java            ← items, paymentMethod, shipping
    ├── OrderItemRequest.java              ← productId, quantity
    ├── ShippingRequest.java               ← address, city, postalCode, country
    ├── OrderResponse.java
    ├── OrderItemResponse.java
    └── ShippingResponse.java
```

- `ShippingRequest` y `ShippingResponse` son records distintos aunque tengan los mismos campos: el de entrada lleva las anotaciones de validación y el de salida no. *Alternativa descartada:* un solo record, que mezcla las reglas de entrada con el contrato de salida.
- `OrderController` lleva `@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)` a nivel de clase y recibe `@AuthenticationPrincipal AuthUser`; al service solo le pasa `user.id()`.

### D2. Entidades (reglas de §11)

- `Order`: `Long userId` (no `@ManyToOne User`, regla 3); `@Enumerated(STRING)` en `status` y `paymentMethod` (regla 7); `total` `BigDecimal` con `precision = 12, scale = 2` (regla 8); los cuatro `shipping*` como columnas; `createdAt` `Instant`; `@OneToMany(mappedBy = "order", cascade = CascadeType.ALL) @OrderBy("id") List<OrderItem> items` con `addItem(item)`, que mantiene los dos lados sincronizados (regla 4).
- `OrderItem`: `@ManyToOne(fetch = LAZY) Order order`, `@ManyToOne(fetch = LAZY) Product product`, `quantity`, `unitPrice` `BigDecimal(12,2)`.
- Igual que `User`: constructor vacío `protected` y setters; el service (mismo paquete) construye la orden. `OrderItem`, en cambio, tiene un constructor público `(Product, Integer quantity, BigDecimal unitPrice)`: con tres tipos distintos no hay riesgo de intercambiar argumentos, y el ítem no cambia después de crearse. Su `setOrder` es de paquete y solo lo usa `Order.addItem`. Sin `equals` / `hashCode` / `toString` (regla 10).
- `@OrderBy("id")` devuelve los ítems en el orden de inserción, que es el del request.

### D3. Descuento de stock atómico

```java
// ProductRepository
@Modifying
@Query("UPDATE Product p SET p.stock = p.stock - :quantity WHERE p.id = :id AND p.stock >= :quantity")
int decreaseStock(@Param("id") Long id, @Param("quantity") int quantity);
```

- Una sola sentencia: la base de datos bloquea la fila durante el `UPDATE`, así que dos compras simultáneas de la última unidad no pueden pasar las dos el `WHERE stock >= :quantity`. Devuelve `0` si no alcanza → `INSUFFICIENT_STOCK`.
- Sin `clearAutomatically` y **sin** leer `product.getStock()` ni hacer `save(product)` después (regla 6 y §11.4): el `Product` cargado en memoria tiene el stock viejo, y un `save` lo pisaría. El mapper de órdenes no lee `stock`.
- *Alternativa descartada:* `@Version` / bloqueo optimista, que exige reintentos y más código (§11.4).

### D4. `OrderServiceImpl.create` (§10)

```java
if (request.items().isEmpty())                     → EMPTY_ORDER
firstDuplicatedProductId(request.items())          → DUPLICATE_ORDER_ITEM(id)
if (!userRepository.existsById(userId))            → AUTH_TOKEN_INVALID
Order order = new Order(); ... createdAt = Instant.now(clock).truncatedTo(MICROS)
for (OrderItemRequest item : request.items()) {    // orden del request
    Product product = productRepository.findById(item.productId()) → PRODUCT_NOT_FOUND(id)
    if (productRepository.decreaseStock(product.getId(), item.quantity()) == 0) → INSUFFICIENT_STOCK(id)
    order.addItem(newItem(product, item.quantity(), product.getPrice()))
    total = total.add(product.getPrice().multiply(BigDecimal.valueOf(item.quantity())))
}
order.setTotal(total.setScale(2, RoundingMode.HALF_UP));
return orderMapper.toResponse(orderRepository.save(order));
```

- **Las tres operaciones empiezan con `requireExistingUser(userId)`** (`existsById` → `AUTH_TOKEN_INVALID`). En `create` va después de `EMPTY_ORDER` y `DUPLICATE_ORDER_ITEM` (orden de §7.4/§10); en `findAllByUser` y `findByIdForUser` va primero, antes de buscar la orden. §7.4 solo lo exigía al crear (allí evita un 500 por la clave foránea), pero un token cuyo usuario ya no existe es un token inválido en cualquier ruta: así un usuario borrado nunca recibe 200 con `[]` ni 404, y `/orders` se comporta igual que `/users/me`. *Decisión del usuario en la revisión del change.* *Alternativa descartada:* comprobarlo solo al crear, que dejaba respuestas "exitosas" a un usuario inexistente.
- `@Transactional` a nivel de clase: si un ítem falla, la excepción hace rollback de los `decreaseStock` anteriores. `findAllByUser` y `findByIdForUser` llevan `@Transactional(readOnly = true)`.
- `firstDuplicatedProductId` recorre los ítems con un `HashSet` y devuelve el primer `productId` que ya se había visto.
- `createdAt` truncado a microsegundos, como en `user-registration` (lo que devuelve el `POST` coincide con el `GET`).

### D5. Consultas sin N+1

```java
@EntityGraph(attributePaths = {"items", "items.product"})
List<Order> findAllByUserIdOrderByCreatedAtDescIdDesc(Long userId);

@EntityGraph(attributePaths = {"items", "items.product"})
Optional<Order> findByIdAndUserId(Long id, Long userId);
```

- Una sola consulta trae órdenes, ítems y productos (regla 5).
- `findByIdAndUserId` resuelve "no existe" y "es de otro usuario" en la misma consulta → el mismo 404 (§7).
- Desempate por `id` descendente: dos órdenes con el mismo `createdAt` (posible en pruebas rápidas) salen siempre en el mismo orden.

### D6. `OrderMapper`

- `subtotal = unitPrice.multiply(BigDecimal.valueOf(quantity))`: con `unitPrice` de escala 2 el resultado ya tiene 2 decimales (`25.00 × 2 = 50.00`).
- Toma `sku`, `name` e `imageKey` del `Product` del ítem (dentro de la transacción, regla 1).
- No incluye `userId` (§7.5).

### D7. Validación de `CreateOrderRequest` (§7.4 y §9.2)

- `@NotNull List<@NotNull @Valid OrderItemRequest> items`, **sin** `@NotEmpty`: la lista vacía la rechaza el service con `EMPTY_ORDER`, como pide §7.4.
- `OrderItemRequest`: `@NotNull Long productId`; `@NotNull @Min(1) @Max(99) Integer quantity` con `must be between 1 and 99`.
- `@NotNull PaymentMethod paymentMethod`; un valor desconocido lo traduce el `GlobalExceptionHandler` a `must be one of: CARD, CASH, TRANSFER`.
- `@NotNull @Valid ShippingRequest shipping`, con `@NotBlank` + `@Size` en `address` (5–120) y `city` (2–60), y `@NotBlank` + `@Pattern` en `postalCode` (`ValidationPatterns.POSTAL_CODE`) y `country` (`ValidationPatterns.COUNTRY`), con los mensajes de §7.4.

### D8. Datos compartidos en los tests de integración

Los `*IT` de órdenes crean órdenes y descuentan stock en el PostgreSQL compartido. `support/SeedData.restoreOrdersAndStock(jdbc)` se llama en `@AfterEach`:

```sql
DELETE FROM order_items WHERE order_id > 2;
DELETE FROM orders WHERE id > 2;
UPDATE products SET stock = CASE id WHEN 1 THEN 10 WHEN 2 THEN 15 WHEN 3 THEN 0
                                    WHEN 4 THEN 20 WHEN 5 THEN 1 WHEN 6 THEN 8 END;
```

y después borra los usuarios `@registration.it` (las órdenes van primero por la clave foránea). Los valores son los de la tabla de §13. *Alternativa descartada:* volver a ejecutar `V4__seed_data.sql`, que duplicaría usuarios y productos sin un `TRUNCATE` previo: eso es justamente `/test/reset`, que llega con `testing-profile` y podrá reemplazar este helper.

### D9. Tests por capa

| Test | Tipo | Qué verifica |
|---|---|---|
| `ProductRepositoryIT` (ampliado) | Testcontainers | `decreaseStock` devuelve 1 y descuenta; devuelve 0 si no alcanza y no cambia el stock (restaura en `@AfterEach`) |
| `OrderRepositoryIT` | Testcontainers | Órdenes semilla de `buyer` en orden descendente con ítems y productos; `findByIdAndUserId(1, 1)` vacío; guardar una orden con `addItem` persiste los ítems con su `order_id` |
| `OrderMapperTest` | Unitario | `subtotal`, campos del producto, `shipping`, sin `userId`, contrato `order-response` |
| `OrderServiceImplTest` | Unitario (Mockito, reloj fijo) | Cada regla de §7.4 y su orden; `unitPrice` del catálogo; total; `decreaseStock` no se llama tras un error previo; usuario inexistente → `AUTH_TOKEN_INVALID` en las tres operaciones, antes de consultar órdenes |
| `OrderControllerTest` | `@WebMvcTest` + `WebMvcSecurityConfig` | 201/200 y contratos; validaciones anidadas; errores de tipo; 404, 409 y 401 desde el service; sin token → 401; `PUT` → 405; `abc` → 400 |
| `OrdersIT` | `IntegrationTest` | Flujo completo de la spec, incluido el rollback y la compra simultánea de la última unidad con dos hilos |

## Risks / Trade-offs

- [Los tests de órdenes modifican datos que otros tests leen] → `SeedData.restoreOrdersAndStock` en `@AfterEach`; Gradle ejecuta las clases de test una detrás de otra (sin paralelismo), así que ningún otro test ve los datos a medias.
- [El test de concurrencia puede ser inestable] → Usa un `CountDownLatch` para lanzar las dos peticiones a la vez y solo comprueba lo que la spec garantiza (una 201, una 409, stock 0), no cuál de las dos gana.
- [Un token emitido antes de un `/test/reset` puede apuntar a otro usuario con el mismo id] → Limitación documentada en §7.6; no aplica hasta `testing-profile`.
- [Las consultas de órdenes hacen una consulta extra (`existsById`) por petición] → Es una búsqueda por clave primaria, despreciable frente a la consulta de órdenes; a cambio, el comportamiento con usuarios borrados es coherente en todas las rutas.
