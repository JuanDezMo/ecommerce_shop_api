# Proposal

## Why

Es el objetivo del MVP: la app tiene un carrito local y necesita convertirlo en una orden, y luego mostrar el historial del usuario. También es el endpoint más rico para practicar pruebas de API: validación anidada (`items[0].quantity`, `shipping.city`), reglas de negocio (orden vacía, producto repetido, stock insuficiente), cálculos verificables (`subtotal`, `total`), aislamiento entre usuarios (la orden de otro responde 404) y concurrencia (dos compras de la última unidad).

## What Changes

- `POST /api/v1/orders` (protegido): crea una orden `CONFIRMED` con los ítems del carrito, la forma de pago y la dirección de envío, descuenta el stock de forma atómica y responde **201** con el `OrderResponse`.
- `GET /api/v1/orders` (protegido): órdenes del usuario del token, de la más reciente a la más antigua, sin paginación.
- `GET /api/v1/orders/{id}` (protegido): una orden del usuario; si no existe **o es de otro usuario**, 404 `ORDER_NOT_FOUND`.
- En las tres rutas, si el usuario del token ya no existe, la respuesta es 401 `AUTH_TOKEN_INVALID` (nunca 200 ni 404), igual que en `/users/me`.
- Reglas de §7.4: validación de campos (400), `EMPTY_ORDER` (400), `DUPLICATE_ORDER_ITEM` (400), usuario del token inexistente (401 `AUTH_TOKEN_INVALID`), `PRODUCT_NOT_FOUND` (404) e `INSUFFICIENT_STOCK` (409), en ese orden; si varios ítems fallan, se reporta el primero del request.
- El precio de cada ítem se copia del producto en el momento de la compra (`unitPrice`); el `total` lo calcula el backend; la dirección se copia en la orden.
- Si cualquier ítem falla, **no** se descuenta stock de ningún producto (rollback).
- Clases nuevas en `order`: `Order`, `OrderItem`, `OrderStatus`, `PaymentMethod`, `OrderRepository`, `OrderService` / `OrderServiceImpl`, `OrderMapper`, `OrderController` y los DTOs. `ProductRepository` gana `decreaseStock`.
- **Infraestructura de pruebas**: un helper que, tras cada test que crea órdenes, borra las órdenes nuevas y devuelve el stock a los valores semilla, porque `ProductCatalogIT` y `SeedDataIT` dependen de ellos y `/test/reset` aún no existe.

## Capabilities

### New Capabilities
- `orders`: creación de órdenes a partir del carrito y consulta del historial propio, con sus validaciones, reglas de stock y cálculos.

### Modified Capabilities
<!-- Ninguna. token-authentication ya protege toda ruta no pública; error-handling ya define los 400 de campos anidados y de tipo; seed-data no cambia. -->

## Impact

- **Código nuevo**: paquete `order` completo y sus tests.
- **Código modificado**: `ProductRepository` (`decreaseStock`), `OpenApiIT`.
- **API**: tres endpoints protegidos bajo `/api/v1/orders`. `PUT /api/v1/orders` con token pasa de 404 a 405, porque la ruta ahora existe.
- **Base de datos**: ninguna migración; se usan `orders` y `order_items` de V3 y las 2 órdenes semilla de `buyer`.
- **Changes posteriores**: `testing-profile` reemplazará la limpieza manual de los tests por `/test/reset`.
