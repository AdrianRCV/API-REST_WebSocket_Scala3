# Sistema de Procesamiento de Eventos y Métricas en Tiempo Real

[![CI](https://github.com/AdrianRCV/API-REST_WebSocket_Scala3/actions/workflows/ci.yml/badge.svg)](https://github.com/AdrianRCV/API-REST_WebSocket_Scala3/actions/workflows/ci.yml)

Backend en Scala 3 (sbt) para procesamiento de eventos y métricas en tiempo real, expuesto vía API HTTP y WebSockets.

## Stack

- Http4s + Tapir — HTTP y contratos OpenAPI
- Doobie + PostgreSQL — persistencia
- Cats Effect — concurrencia y efectos
- FS2 — procesamiento de streams

## Requisitos

- JDK 17+
- sbt 1.12.x
- Docker (para levantar PostgreSQL local)

## Desarrollo

```bash
docker compose up -d      # levanta PostgreSQL local
sbt run                   # arranca el servidor en http://localhost:8080
```

`sbt run` requiere que PostgreSQL esté accesible al arrancar: las migraciones de esquema
(Flyway) se aplican automáticamente antes de levantar el servidor HTTP. La conexión se
configura vía variables de entorno, con valores por defecto que coinciden con
`docker-compose.yml`:

| Variable            | Default                                          |
|---------------------|---------------------------------------------------|
| `DATABASE_URL`      | `jdbc:postgresql://localhost:5432/event_metrics`  |
| `DATABASE_USER`     | `event_metrics`                                    |
| `DATABASE_PASSWORD` | `event_metrics`                                    |

- Health check: `GET http://localhost:8080/health`
- Documentación OpenAPI (Swagger UI): `http://localhost:8080/docs`

## Endpoints

- `POST /events` — crea un evento (`ServerMetric`, `LogEntry` o `CustomMetric`) y devuelve el evento creado (`201 Created`).
- `GET /events` — lista todos los eventos almacenados.
- `GET /events/{id}` — obtiene un evento por id (`404 Not Found` si no existe).
- `GET /events/stream` — upgrade a WebSocket; retransmite en tiempo real los eventos recién creados como frames de texto JSON (solo servidor→cliente, sin backfill de eventos anteriores).

## Limitaciones conocidas

- La suscripción de un cliente WebSocket usa una cola acotada (`Topic.subscribe`, `maxQueued = 16`). Si un cliente WS se queda colgado o es lento leyendo, su cola puede llenarse y eso puede terminar bloqueando `POST /events`, ya que la publicación al broadcaster ocurre dentro del propio handler HTTP tras el insert en base de datos. Es una limitación aceptada para esta iteración (instancia única).
- **3 tests fallan en CI (GitHub Actions) por una causa aún no resuelta**: `MigrationsSpec`, `DoobieEventRepositorySpec` y `EventsApiSpec` (todos basados en `testcontainers-scala-postgresql`) fallan de forma consistente con `Connection refused` durante toda la ventana de timeout (verificado con logs reales, no es un problema de que el timeout sea demasiado corto — ya se probó subiéndolo a 2 minutos sin efecto). El contenedor de Postgres que levanta testcontainers nunca llega a aceptar conexiones en ese runner. Es el mismo síntoma estructural observado en el entorno de desarrollo local, ahora confirmado también en un runner de GitHub limpio — así que no es (solo) una peculiaridad del sandbox de desarrollo. Causa raíz pendiente de investigar (candidatos: la imagen de Postgres por defecto de `testcontainers-scala-postgresql`, la wait strategy del contenedor, o algo específico de red en el runner). Queda como pendiente para una iteración dedicada.

## Tests

```bash
sbt test
```
