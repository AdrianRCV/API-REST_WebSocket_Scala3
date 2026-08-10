# Sistema de Procesamiento de Eventos y Métricas en Tiempo Real

Backend en Scala 3 (sbt) para procesamiento de eventos y métricas en tiempo real, expuesto vía API HTTP y (próximamente) WebSockets.

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

## Tests

```bash
sbt test
```
