package com.adrian.eventmetrics.infrastructure.db

import cats.effect.Sync
import cats.syntax.flatMap.*
import cats.syntax.functor.*
import org.flywaydb.core.Flyway
import org.typelevel.log4cats.Logger

object Migrations:
  // `.connectRetries(10)` tolerates a not-yet-ready Postgres (e.g. container still starting),
  // but Flyway's default retry interval grows up to 120s between attempts — without logging,
  // a hang here (wrong DATABASE_URL, DB down, ...) is silent and looks like the app froze.
  def migrate[F[_]: Sync: Logger](config: DatabaseConfig): F[Unit] =
    Logger[F].info("Connecting to database and applying migrations...") >>
      Sync[F].blocking {
        Flyway
          .configure()
          .dataSource(config.jdbcUrl, config.user, config.password)
          .connectRetries(10)
          .load()
          .migrate()
      }.void >>
      Logger[F].info("Database migrations applied successfully")
