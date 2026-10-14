# Proposal

## Why

Las apps (Android/iOS) necesitan mostrar el catálogo antes de cualquier login, y las suites de pruebas de API necesitan un primer endpoint real y público para practicar escenarios felices, negativos y de contrato. Además, es la primera feature con todas las capas (Controller → Service → ServiceImpl → Repository → Entity + Mapper), así que deja la **plantilla** que copiarán `user-registration` y `orders`, y es el primer change en el que las reglas de ArchUnit revisan código real.

## What Changes

- Nuevo endpoint público `GET /api/v1/products`: devuelve los 6 productos semilla ordenados por `id`, sin paginación ni filtros.
- Nuevo endpoint público `GET /api/v1/products/{id}`: devuelve un producto o 404 `PRODUCT_NOT_FOUND` (`Product {id} not found`); un `id` no numérico responde 400 `VALIDATION_ERROR`.
- Paquete `com.shoplab.api.product` con `Product` (entidad), `ProductRepository`, `ProductService` / `ProductServiceImpl`, `ProductMapper`, `ProductController` y `dto/ProductResponse`.
- Las respuestas cumplen los contratos que ya existen: `docs/contracts/product-response.schema.json` y `product-list.schema.json`. **No** cambian los esquemas ni el `ErrorCode` (`PRODUCT_NOT_FOUND` ya existe).
- `ArchitectureTest` gana reglas que solo tienen sentido con código real: cada `ServiceImpl` es `@Transactional` a nivel de clase e implementa su interfaz, y los services no devuelven `Optional` ni entidades.

## Capabilities

### New Capabilities
- `product-catalog`: consulta pública del catálogo de productos (listado y detalle), con su formato de respuesta y sus errores.

### Modified Capabilities
<!-- Ninguna: los requisitos de error-handling (404 con id, 400 por id no numérico, 405) ya cubren los errores que usa este change. -->

## Impact

- **Código nuevo**: `src/main/java/com/shoplab/api/product/**`; tests en `src/test/java/com/shoplab/api/product/**`.
- **Código modificado**: `ArchitectureTest` (reglas nuevas).
- **API**: dos endpoints `GET` públicos bajo `/api/v1/products`. Con el `SecurityConfig` provisional ya son públicos; `auth-jwt-filter` deberá mantenerlos en `permitAll` (§7.1).
- **Base de datos**: ninguna migración nueva; se usa la tabla `products` de V2 y los datos de V4.
- **Dependencias**: ninguna nueva.
- **Changes posteriores**: `orders` reutiliza `Product` y `ProductRepository` (descuento atómico de stock, §3.3); este change no incluye ese método.
