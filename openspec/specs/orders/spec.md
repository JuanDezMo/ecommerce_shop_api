# orders Specification

## Purpose

Permite que un usuario autenticado convierta su carrito en una orden confirmada, con el stock descontado de forma segura y los importes calculados por el backend, y que consulte solo su propio historial de órdenes.

## Requirements

### Requirement: Crear una orden
`POST /api/v1/orders` con un token válido y un body válido SHALL crear una orden con `status` `CONFIRMED` y responder 201 con la orden creada. La respuesta MUST cumplir `docs/contracts/order-response.schema.json`, MUST NOT incluir `userId`, y `items` MUST mantener el orden del request.

#### Scenario: Orden con dos productos
- **WHEN** `standard@shoplab.dev` envía `{"items":[{"productId":1,"quantity":2},{"productId":4,"quantity":1}],"paymentMethod":"CARD","shipping":{"address":"Calle 10 #20-30","city":"Bogotá","postalCode":"110111","country":"CO"}}`
- **THEN** la respuesta es 201, cumple `order-response.schema.json`, `status` es `CONFIRMED`, `items` tiene el producto 1 y luego el 4 con su `sku`, `name` e `imageKey`, y `shipping` es igual al enviado

#### Scenario: Sin userId en la respuesta
- **WHEN** se crea una orden
- **THEN** el body no contiene la clave `userId`

### Requirement: Importes calculados por el backend
Cada ítem SHALL tener `unitPrice` igual al precio del producto en el momento de la compra y `subtotal` igual a `unitPrice × quantity`. `total` MUST ser la suma de los `subtotal`, con 2 decimales. El backend MUST ignorar cualquier precio o total que envíe el cliente.

#### Scenario: Cálculo del total
- **WHEN** se compran 2 unidades del producto 1 (59.99) y 1 del producto 4 (25.00)
- **THEN** los `subtotal` son `119.98` y `25.00` y `total` es `144.98`

#### Scenario: Precio enviado por el cliente
- **WHEN** el body incluye además `"total": 1` y `"unitPrice": 0.01` en un ítem
- **THEN** la orden se crea con los precios del catálogo

### Requirement: Descuento de stock
Al crear la orden, el stock de cada producto SHALL disminuir en la cantidad comprada.

#### Scenario: Stock después de comprar
- **WHEN** se compran 2 unidades del producto 1 (stock 10)
- **THEN** `GET /api/v1/products/1` devuelve `stock` 8

### Requirement: Stock insuficiente
Si la cantidad pedida de un producto supera su stock, la creación SHALL responder 409 `INSUFFICIENT_STOCK` con `message` `Insufficient stock for product {id}`. En ese caso MUST NOT crearse la orden ni descontarse stock de ningún producto del request.

#### Scenario: Producto agotado
- **WHEN** se pide 1 unidad del producto 3 (stock 0)
- **THEN** la respuesta es 409 `INSUFFICIENT_STOCK` con `message` `Insufficient stock for product 3`

#### Scenario: Más unidades que el stock
- **WHEN** se piden 2 unidades del producto 5 (stock 1)
- **THEN** la respuesta es 409 `INSUFFICIENT_STOCK` con `message` `Insufficient stock for product 5`

#### Scenario: Rollback de los ítems anteriores
- **WHEN** se piden 1 unidad del producto 1 y luego 1 unidad del producto 3 (agotado)
- **THEN** la respuesta es 409 y el stock del producto 1 sigue siendo 10

### Requirement: Sin sobreventa con compras simultáneas
Si dos órdenes piden a la vez la última unidad de un producto, exactamente una SHALL crearse y la otra MUST responder 409 `INSUFFICIENT_STOCK`. El stock MUST NOT quedar negativo.

#### Scenario: Última unidad
- **WHEN** dos peticiones simultáneas piden 1 unidad del producto 5 (stock 1)
- **THEN** una responde 201, la otra 409 `INSUFFICIENT_STOCK`, y el stock del producto 5 queda en 0

### Requirement: Producto inexistente
Si un ítem referencia un producto que no existe, la creación SHALL responder 404 `PRODUCT_NOT_FOUND` con `message` `Product {id} not found`, sin crear la orden ni descontar stock.

#### Scenario: Producto 99
- **WHEN** un ítem tiene `productId` 99
- **THEN** la respuesta es 404 `PRODUCT_NOT_FOUND` con `message` `Product 99 not found`

### Requirement: Orden vacía
Si `items` es una lista vacía, la creación SHALL responder 400 `EMPTY_ORDER` con `message` `Order must contain at least one item` y `fieldErrors` vacío.

#### Scenario: Carrito vacío
- **WHEN** un body válido trae `"items": []`
- **THEN** la respuesta es 400 `EMPTY_ORDER` con `fieldErrors` `[]`

### Requirement: Producto repetido
Si dos ítems tienen el mismo `productId`, la creación SHALL responder 400 `DUPLICATE_ORDER_ITEM` con `message` `Product {id} appears more than once in the order`, donde `{id}` es el primer `productId` repetido según el orden del request.

#### Scenario: Producto 1 dos veces
- **WHEN** `items` es `[{"productId":1,"quantity":1},{"productId":4,"quantity":1},{"productId":1,"quantity":2}]`
- **THEN** la respuesta es 400 `DUPLICATE_ORDER_ITEM` con `message` `Product 1 appears more than once in the order`

### Requirement: Validación del body de la orden
`items` (no nulo, sin elementos nulos), `productId` (no nulo), `quantity` (de 1 a 99), `paymentMethod` (`CARD`, `CASH` o `TRANSFER`) y `shipping` con `address` (5 a 120), `city` (2 a 60), `postalCode` (3 a 10 letras, dígitos, espacios o guiones) y `country` (lista soportada) SHALL validarse antes que cualquier regla de negocio. Si alguno falla, la respuesta MUST ser 400 `VALIDATION_ERROR` con todos los errores.

#### Scenario: Cantidad fuera de rango
- **WHEN** el primer ítem tiene `quantity` 0 o 100
- **THEN** la respuesta es 400 con `{"field":"items[0].quantity","message":"must be between 1 and 99"}`

#### Scenario: Ítem nulo
- **WHEN** `items` es `[null]`
- **THEN** la respuesta es 400 con `{"field":"items[0]","message":"must not be null"}`

#### Scenario: Items nulo
- **WHEN** `items` es `null`
- **THEN** la respuesta es 400 con `{"field":"items","message":"must not be null"}`

#### Scenario: Envío inválido
- **WHEN** `shipping` tiene `city` `B` y `postalCode` `1`
- **THEN** la respuesta es 400 con `{"field":"shipping.city","message":"size must be between 2 and 60"}` y `{"field":"shipping.postalCode","message":"must be 3 to 10 letters, digits, spaces or hyphens"}`

#### Scenario: Forma de pago desconocida
- **WHEN** `paymentMethod` es `CRYPTO`
- **THEN** la respuesta es 400 con `{"field":"paymentMethod","message":"must be one of: CARD, CASH, TRANSFER"}`

#### Scenario: Validación antes que orden vacía
- **WHEN** `items` es `[]` y falta `shipping`
- **THEN** la respuesta es 400 `VALIDATION_ERROR` con `{"field":"shipping","message":"must not be null"}`, no `EMPTY_ORDER`

### Requirement: Orden de evaluación de la creación
Tras la validación, las reglas SHALL evaluarse en este orden: orden vacía, producto repetido, usuario del token existente, y luego cada ítem en el orden del request (producto existente y después stock suficiente). Se responde solo el primer error.

#### Scenario: Primer ítem sin stock y segundo inexistente
- **WHEN** `items` es `[{"productId":3,"quantity":1},{"productId":99,"quantity":1}]`
- **THEN** la respuesta es 409 `INSUFFICIENT_STOCK` para el producto 3

#### Scenario: Primer ítem inexistente y segundo sin stock
- **WHEN** `items` es `[{"productId":99,"quantity":1},{"productId":3,"quantity":1}]`
- **THEN** la respuesta es 404 `PRODUCT_NOT_FOUND` para el producto 99

#### Scenario: Repetido antes que inexistente
- **WHEN** `items` es `[{"productId":99,"quantity":1},{"productId":99,"quantity":1}]`
- **THEN** la respuesta es 400 `DUPLICATE_ORDER_ITEM`

### Requirement: Usuario del token inexistente
Si el usuario del token ya no existe, todas las rutas de órdenes (`POST /api/v1/orders`, `GET /api/v1/orders` y `GET /api/v1/orders/{id}`) SHALL responder 401 `AUTH_TOKEN_INVALID` con `message` `Authentication token is invalid`. Al crear, MUST NOT crearse la orden ni descontarse stock. En las consultas, esta comprobación MUST ir antes de buscar la orden, así un usuario borrado nunca recibe 200 ni 404.

#### Scenario: Usuario borrado crea una orden
- **WHEN** un usuario borrado de la base de datos envía una orden válida con su token
- **THEN** la respuesta es 401 `AUTH_TOKEN_INVALID` y el stock no cambia

#### Scenario: Usuario borrado consulta su historial
- **WHEN** un usuario borrado de la base de datos hace `GET /api/v1/orders` con su token
- **THEN** la respuesta es 401 `AUTH_TOKEN_INVALID`, no 200 con `[]`

#### Scenario: Usuario borrado consulta una orden
- **WHEN** un usuario borrado de la base de datos hace `GET /api/v1/orders/1` con su token
- **THEN** la respuesta es 401 `AUTH_TOKEN_INVALID`, no 404

### Requirement: Historial de órdenes
`GET /api/v1/orders` SHALL devolver las órdenes del usuario del token, ordenadas por `createdAt` de la más reciente a la más antigua, sin paginación. El body MUST cumplir `docs/contracts/order-list.schema.json`.

#### Scenario: Historial semilla de buyer
- **WHEN** `buyer@shoplab.dev` hace `GET /api/v1/orders`
- **THEN** recibe 2 órdenes: primero la 2 (`CASH`, total `45.00`) y luego la 1 (`CARD`, total `109.99`)

#### Scenario: Usuario sin órdenes
- **WHEN** `standard@shoplab.dev` hace `GET /api/v1/orders` sin haber comprado
- **THEN** la respuesta es 200 con `[]`

#### Scenario: Orden recién creada
- **WHEN** un usuario crea una orden y luego consulta `GET /api/v1/orders`
- **THEN** la orden nueva aparece primera

### Requirement: Detalle de una orden propia
`GET /api/v1/orders/{id}` SHALL devolver la orden si pertenece al usuario del token. Si no existe o es de otro usuario, MUST responder 404 `ORDER_NOT_FOUND` con `message` `Order {id} not found`, sin revelar que existe.

#### Scenario: Orden propia
- **WHEN** `buyer@shoplab.dev` hace `GET /api/v1/orders/1`
- **THEN** la respuesta es 200 con los ítems del producto 1 (`quantity` 1, `subtotal` `59.99`) y del producto 4 (`quantity` 2, `subtotal` `50.00`) y `total` `109.99`

#### Scenario: Orden de otro usuario
- **WHEN** `standard@shoplab.dev` hace `GET /api/v1/orders/1`
- **THEN** la respuesta es 404 `ORDER_NOT_FOUND` con `message` `Order 1 not found`

#### Scenario: Orden inexistente
- **WHEN** se hace `GET /api/v1/orders/999`
- **THEN** la respuesta es 404 `ORDER_NOT_FOUND`

#### Scenario: Id no numérico
- **WHEN** se hace `GET /api/v1/orders/abc` con un token válido
- **THEN** la respuesta es 400 con `fieldErrors` `[{"field":"id","message":"must be an integer"}]`

### Requirement: Rutas de órdenes protegidas
Todas las rutas de órdenes SHALL exigir un token válido, y solo admiten los métodos definidos: con token, cualquier otro método MUST responder 405.

#### Scenario: Sin token
- **WHEN** se hace `POST /api/v1/orders` o `GET /api/v1/orders` sin cabecera `Authorization`
- **THEN** la respuesta es 401 `AUTH_TOKEN_MISSING`

#### Scenario: Método no soportado con token
- **WHEN** se hace `PUT /api/v1/orders` con un token válido
- **THEN** la respuesta es 405 `METHOD_NOT_ALLOWED`
