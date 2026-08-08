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

- Health check: `GET http://localhost:8080/health`
- Documentación OpenAPI (Swagger UI): `http://localhost:8080/docs`

## Tests

```bash
sbt test
```
