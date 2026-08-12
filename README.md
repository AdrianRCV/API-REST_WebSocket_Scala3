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
- **3 tests fallan en CI (GitHub Actions) por una causa aún no resuelta — por eso el badge de CI aparece en rojo**: `MigrationsSpec`, `DoobieEventRepositorySpec` y `EventsApiSpec` (todos basados en `testcontainers-scala-postgresql`) fallan de forma consistente con `Connection refused` durante ~121s, coincidiendo con el timeout de arranque/conexión por defecto de testcontainers-java (120s). Ese timeout gobierna la llamada síncrona y bloqueante `container.start()`, que ocurre *antes* de construir el efecto `IO` — por eso subir `munitIOTimeout` a 2 minutos en los 3 specs (ver comentario en el código) no tuvo efecto sobre este fallo concreto: esa protección solo cubre el trabajo efectual que corre una vez el contenedor ya está arriba, nunca la fase de arranque en sí. El contenedor de Postgres nunca llega a aceptar conexiones dentro de esos 120s en el runner. Es el mismo síntoma estructural observado en el entorno de desarrollo local, ahora confirmado también en un runner de GitHub limpio. **Candidato principal a investigar primero**: `testcontainers-scala-postgresql` 0.39.12 resuelve a testcontainers-java 1.16.2 (noviembre 2021), cuya imagen de Postgres por defecto es `postgres:9.6.12` (EOL) — un runtime de hace varios años contra un Docker/kernel moderno es un sospechoso mucho más probable que algo específico de red del runner. Subir a una versión moderna de `testcontainers-scala` (o fijar explícitamente una imagen de Postgres actual) es el primer experimento a probar en la iteración dedicada. Queda pendiente.

## Tests

```bash
sbt test
```
