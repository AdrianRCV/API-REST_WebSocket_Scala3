package com.adrian.eventmetrics.infrastructure.db

import cats.effect.IO
import com.dimafeng.testcontainers.PostgreSQLContainer
import munit.CatsEffectSuite
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import scala.concurrent.duration.*

class MigrationsSpec extends CatsEffectSuite:

  // Note: this does NOT protect the blocking `container.start()` call below — that call runs
  // synchronously, before this IO is even constructed, so munitIOTimeout can never interrupt it.
  // Testcontainers' own startup/connect timeouts (120s by default) govern that phase instead —
  // see README "Limitaciones conocidas" for the CI failure this relates to. This override only
  // extends the window for the IO-effectful work that runs once the container is actually up
  // (e.g. Flyway's own connection retries in Migrations.migrate).
  override def munitIOTimeout: Duration = 2.minutes

  private given Logger[IO] = Slf4jLogger.getLogger[IO]

  test("migrate creates the events table") {
    val container = PostgreSQLContainer()
    container.start()
    try
      val config = DatabaseConfig(container.jdbcUrl, container.username, container.password)
      for
        _ <- Migrations.migrate[IO](config)
        tableExists <- IO.blocking {
          val conn = java.sql.DriverManager.getConnection(config.jdbcUrl, config.user, config.password)
          try
            val rs = conn.getMetaData.getTables(null, null, "events", null)
            rs.next()
          finally conn.close()
        }
      yield assert(tableExists, "expected 'events' table to exist after migration")
    finally container.stop()
  }
