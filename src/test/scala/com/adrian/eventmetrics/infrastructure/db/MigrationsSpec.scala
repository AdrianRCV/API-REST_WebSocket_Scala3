package com.adrian.eventmetrics.infrastructure.db

import cats.effect.IO
import com.dimafeng.testcontainers.PostgreSQLContainer
import munit.CatsEffectSuite
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import scala.concurrent.duration.*

class MigrationsSpec extends CatsEffectSuite:

  // CI runners can take well over munit-cats-effect's default 30s munitIOTimeout for the
  // Postgres testcontainer to actually accept connections (container "started" fires before
  // Postgres finishes its real startup). Override the timeout that actually governs IO
  // cancellation per the deprecation note on munitTimeout in CatsEffectSuite.
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
