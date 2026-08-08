package com.adrian.eventmetrics.infrastructure.db

import cats.effect.Sync

final case class DatabaseConfig(jdbcUrl: String, user: String, password: String)

object DatabaseConfig:
  def fromEnv[F[_]: Sync]: F[DatabaseConfig] =
    Sync[F].delay {
      DatabaseConfig(
        jdbcUrl = sys.env.getOrElse("DATABASE_URL", "jdbc:postgresql://localhost:5432/event_metrics"),
        user = sys.env.getOrElse("DATABASE_USER", "event_metrics"),
        password = sys.env.getOrElse("DATABASE_PASSWORD", "event_metrics")
      )
    }
