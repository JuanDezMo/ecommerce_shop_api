# product-catalog Specification

## Purpose

Permite a las apps y a las suites de pruebas consultar, sin autenticación, el catálogo de productos (listado completo y detalle por id), con un formato de respuesta estable y verificable contra los contratos JSON.

## Requirements

### Requirement: Listado del catálogo
`GET /api/v1/products` SHALL responder 200 con un array JSON de todos los productos, ordenados por `id` ascendente, sin paginación ni filtros. El body MUST cumplir `docs/contracts/product-list.schema.json`.

#### Scenario: Catálogo semilla completo
- **WHEN** se hace `GET /api/v1/products` sobre los datos semilla
- **THEN** la respuesta es 200 y contiene 6 productos con `id` 1, 2, 3, 4, 5 y 6, en ese orden

#### Scenario: Cumple el contrato de listado
- **WHEN** se hace `GET /api/v1/products`
- **THEN** el body cumple `product-list.schema.json`

### Requirement: Detalle de un producto
`GET /api/v1/products/{id}` SHALL responder 200 con el producto de ese id. El body MUST cumplir `docs/contracts/product-response.schema.json`.

#### Scenario: Producto existente
- **WHEN** se hace `GET /api/v1/products/1`
- **THEN** la respuesta es 200 con `{"id":1,"sku":"SL-HEAD-001","name":"Wireless Headphones","description":"Over-ear bluetooth headphones","price":59.99,"stock":10,"imageKey":"headphones"}`

#### Scenario: Cumple el contrato de detalle
- **WHEN** se hace `GET /api/v1/products/4`
- **THEN** el body cumple `product-response.schema.json`

### Requirement: Campos de un producto
Cada producto SHALL exponer exactamente `id`, `sku`, `name`, `description`, `price`, `stock` e `imageKey`, todos presentes y sin campos extra. `price` MUST ser un número JSON en USD con 2 decimales y `stock` un entero mayor o igual que 0.

#### Scenario: Precio con decimales en cero
- **WHEN** se hace `GET /api/v1/products/4`
- **THEN** `price` es el número `25.00` (no el texto `"25.00"`)

#### Scenario: Sin datos internos
- **WHEN** se consulta cualquier producto
- **THEN** la respuesta no incluye `createdAt` ni ningún otro campo fuera de los siete del contrato

### Requirement: Stock visible para productos agotados
Los productos sin unidades SHALL aparecer en el listado y en el detalle con `stock` igual a 0, para que la app pueda mostrarlos como agotados.

#### Scenario: Producto agotado
- **WHEN** se hace `GET /api/v1/products/3`
- **THEN** la respuesta es 200 y `stock` es `0`

#### Scenario: Última unidad
- **WHEN** se hace `GET /api/v1/products/5`
- **THEN** la respuesta es 200 y `stock` es `1`

### Requirement: Producto inexistente
Si no existe un producto con el id pedido, `GET /api/v1/products/{id}` SHALL responder 404 con `code` `PRODUCT_NOT_FOUND` y `message` `Product {id} not found`, donde `{id}` es el id pedido.

#### Scenario: Id fuera del catálogo
- **WHEN** se hace `GET /api/v1/products/99`
- **THEN** la respuesta es 404 `PRODUCT_NOT_FOUND` con `message` `Product 99 not found` y `fieldErrors` `[]`

#### Scenario: Id cero o negativo
- **WHEN** se hace `GET /api/v1/products/0` o `GET /api/v1/products/-1`
- **THEN** la respuesta es 404 `PRODUCT_NOT_FOUND` con el id pedido en el `message`

### Requirement: Id de producto no numérico
Si el `{id}` no es un entero, `GET /api/v1/products/{id}` SHALL responder 400 `VALIDATION_ERROR` con `fieldErrors` `[{"field":"id","message":"must be an integer"}]`.

#### Scenario: Id con letras
- **WHEN** se hace `GET /api/v1/products/abc`
- **THEN** la respuesta es 400 `VALIDATION_ERROR` con `fieldErrors` `[{"field":"id","message":"must be an integer"}]`

#### Scenario: Id decimal
- **WHEN** se hace `GET /api/v1/products/1.5`
- **THEN** la respuesta es 400 `VALIDATION_ERROR` con `fieldErrors` `[{"field":"id","message":"must be an integer"}]`

### Requirement: Catálogo de solo lectura
Las rutas del catálogo SHALL admitir solo `GET`. Cualquier otro método MUST responder 405 `METHOD_NOT_ALLOWED`, con o sin token.

#### Scenario: Intento de borrar un producto
- **WHEN** se hace `DELETE /api/v1/products/1`
- **THEN** la respuesta es 405 `METHOD_NOT_ALLOWED` y el producto sigue existiendo

#### Scenario: Intento de crear un producto
- **WHEN** se hace `POST /api/v1/products` con un body JSON
- **THEN** la respuesta es 405 `METHOD_NOT_ALLOWED`

### Requirement: Acceso público
Las rutas del catálogo SHALL responder sin necesidad de token.

#### Scenario: Sin cabecera Authorization
- **WHEN** se hace `GET /api/v1/products` sin cabecera `Authorization`
- **THEN** la respuesta es 200
