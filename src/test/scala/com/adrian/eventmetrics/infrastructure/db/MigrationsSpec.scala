package com.adrian.eventmetrics.infrastructure.db

import cats.effect.IO
import com.dimafeng.testcontainers.PostgreSQLContainer
import munit.CatsEffectSuite

class MigrationsSpec extends CatsEffectSuite:

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
