package com.adrian.eventmetrics.infrastructure.db

import cats.effect.Sync
import cats.syntax.functor.*
import org.flywaydb.core.Flyway

object Migrations:
  def migrate[F[_]: Sync](config: DatabaseConfig): F[Unit] =
    Sync[F].blocking {
      Flyway
        .configure()
        .dataSource(config.jdbcUrl, config.user, config.password)
        .connectRetries(10)
        .load()
        .migrate()
    }.void
