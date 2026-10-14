# ShopLab · shop-backend

API REST de práctica para automatizar pruebas móviles (Jetpack Compose y SwiftUI) y de API: login con JWT, catálogo, registro y órdenes.

- Arquitectura y reglas de negocio: [`docs/shop-backend-arquitectura.md`](docs/shop-backend-arquitectura.md) (fuente de verdad)
- Contratos JSON para validar respuestas: [`docs/contracts/`](docs/contracts/)
- Guía para los desarrolladores de las apps iOS y Android (endpoints por pantalla, errores, imágenes, simulación de la compra y utilidades de prueba): [`guia-integracion-ios-android.md`](guia-integracion-ios-android.md)
- IDs de accesibilidad comunes a las apps (Appium y Maestro): [`catalogo-ids-accesibilidad.md`](catalogo-ids-accesibilidad.md)
- Planeación por changes: [`openspec/`](openspec/)

> Backend de práctica: **nunca** lo despliegues en un entorno público con el perfil `testing`.

## Requisitos

| Herramienta | Versión | Para qué |
|---|---|---|
| Java (JDK) | 21 | Compilar y correr los tests. Gradle lo busca solo (toolchain) |
| Docker | Docker Desktop o colima | `docker compose` y los tests de integración (Testcontainers) |

No hace falta instalar Gradle: se usa el wrapper `./gradlew`.

**colima (macOS):** `build.gradle.kts` detecta el socket `~/.colima/default/docker.sock` y se lo pasa a Testcontainers. Solo necesitas `colima start` antes de correr los tests de integración.

## Arrancar todo con Docker

```bash
cp .env.example .env          # la primera vez; cambia los valores si quieres
docker compose up --build
```

| URL | Qué es |
|---|---|
| http://localhost:8080/actuator/health | Salud: `{"status":"UP"}` |
| http://localhost:8080/swagger-ui.html | Swagger UI |
| http://localhost:8080/v3/api-docs | OpenAPI en JSON |

Desde el emulador de Android usa `http://10.0.2.2:8080`; desde el simulador de iOS, `http://localhost:8080`.

Para borrar todo, incluida la base de datos: `docker compose down -v`.

## Tests

| Comando | Qué corre | Necesita Docker |
|---|---|---|
| `./gradlew test` | Tests rápidos (`*Test`): unitarios, `@WebMvcTest` y reglas de arquitectura (ArchUnit) | No |
| `./gradlew integrationTest` | Tests de integración (`*IT`) contra PostgreSQL 16 real con Testcontainers | Sí |
| `./gradlew check` | Todos | Sí |

Un test concreto: `./gradlew test --tests ArchitectureTest` o `./gradlew integrationTest --tests SeedDataIT`.

Cada tarea sigue TDD: test que falla → código mínimo → refactor → commit.

## Datos semilla

Se cargan en todos los perfiles. Contraseña de todos los usuarios: `Secret_123`.

| id | Usuario | Estado | Uso |
|---|---|---|---|
| 1 | `standard@shoplab.dev` | ACTIVE | Flujo feliz (sin órdenes) |
| 2 | `locked@shoplab.dev` | LOCKED | Login bloqueado (423) |
| 3 | `buyer@shoplab.dev` | ACTIVE | Tiene 2 órdenes previas |

Hay 6 productos (ids 1 a 6). El producto 3 tiene stock 0 y el 5 tiene stock 1. Detalle completo en §13 del documento de arquitectura.

## Perfiles

| Perfil | Uso |
|---|---|
| `testing` | Por defecto en `docker compose`. Pensado para automatización |
| `dev` | API en el IDE contra `localhost:5432` (`docker compose up db`) |
| `default` | Configuración solo por variables de entorno |
