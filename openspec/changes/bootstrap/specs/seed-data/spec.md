# Spec Delta

## Purpose

Garantiza que la API arranca, en cualquier perfil, con un conjunto fijo y conocido de usuarios, productos y órdenes, para que las apps, la demo y las pruebas automatizadas tengan datos predecibles sin crearlos a mano.

## ADDED Requirements

### Requirement: Usuarios semilla
Tras arrancar en cualquier perfil, SHALL existir tres usuarios con contraseña `Secret_123`: `standard@shoplab.dev` (ACTIVE, sin órdenes), `locked@shoplab.dev` (LOCKED) y `buyer@shoplab.dev` (ACTIVE, con órdenes previas). Sus ids MUST ser 1, 2 y 3 respectivamente. Las contraseñas MUST guardarse solo como hash BCrypt.

#### Scenario: Usuarios disponibles al arrancar
- **WHEN** la API arranca contra una base de datos vacía
- **THEN** existen exactamente esos tres usuarios con esos ids y estados

#### Scenario: Contraseña nunca en texto plano
- **WHEN** se consulta la tabla de usuarios
- **THEN** ningún valor almacenado es `Secret_123` y cada hash verifica `Secret_123` con BCrypt

### Requirement: Catálogo semilla
SHALL existir exactamente 6 productos con ids 1 a 6, precios en USD con 2 decimales y una `imageKey` cada uno, según la tabla de productos semilla del design. Entre ellos MUST haber uno con stock 0 y otro con stock 1.

#### Scenario: Productos disponibles
- **WHEN** la API arranca contra una base de datos vacía
- **THEN** existen 6 productos con los ids, SKUs, precios, stock e `imageKey` definidos

#### Scenario: Casos de stock para pruebas
- **WHEN** se consultan los productos semilla
- **THEN** el producto 3 tiene stock 0 y el producto 5 tiene stock 1

### Requirement: Historial de órdenes semilla
El usuario `buyer@shoplab.dev` SHALL tener 2 órdenes `CONFIRMED` con ítems, `unitPrice` igual al precio del producto, `total` igual a la suma de `unitPrice × quantity` y fechas distintas. Ningún otro usuario MUST tener órdenes semilla.

#### Scenario: Historial de buyer
- **WHEN** la API arranca contra una base de datos vacía
- **THEN** el usuario 3 tiene 2 órdenes y el total de cada una coincide con la suma de sus ítems

#### Scenario: Usuario sin historial
- **WHEN** se consultan las órdenes del usuario 1
- **THEN** no tiene ninguna

### Requirement: Esquema y datos solo por migraciones
El esquema de la base de datos y los datos semilla SHALL crearse solo mediante migraciones versionadas que se aplican una sola vez.

#### Scenario: Arranque idempotente
- **WHEN** la API se reinicia sobre una base de datos ya migrada
- **THEN** no se duplican datos semilla y los ids siguen siendo los mismos

#### Scenario: Restricciones de integridad
- **WHEN** se intenta guardar un producto con stock negativo, un ítem con `quantity` 0 o un email repetido
- **THEN** la base de datos rechaza la operación
