package com.adrian.eventmetrics.infrastructure.persistence

import cats.effect.IO
import com.dimafeng.testcontainers.PostgreSQLContainer
import org.typelevel.doobie.Transactor
import munit.CatsEffectSuite
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import java.time.Instant
import com.adrian.eventmetrics.domain.model.{Event, EventId, LogLevel}
import com.adrian.eventmetrics.infrastructure.db.{DatabaseConfig, Migrations}
import scala.concurrent.duration.*

class DoobieEventRepositorySpec extends CatsEffectSuite:

  // CI runners can take well over munit-cats-effect's default 30s munitIOTimeout for the
  // Postgres testcontainer to actually accept connections (container "started" fires before
  // Postgres finishes its real startup). Override the timeout that actually governs IO
  // cancellation per the deprecation note on munitTimeout in CatsEffectSuite.
  override def munitIOTimeout: Duration = 2.minutes

  private given Logger[IO] = Slf4jLogger.getLogger[IO]

  test("insert then findById and list round-trip all Event variants") {
    val container = PostgreSQLContainer()
    container.start()
    try
      val config = DatabaseConfig(container.jdbcUrl, container.username, container.password)
      val xa = Transactor.fromDriverManager[IO](
        driver = "org.postgresql.Driver",
        url = config.jdbcUrl,
        user = config.user,
        password = config.password,
        logHandler = None
      )
      val repo = new DoobieEventRepository[IO](xa)

      val serverMetricId = EventId.random()
      val logEntryId      = EventId.random()
      val customMetricId  = EventId.random()

      val serverMetric = Event.ServerMetric(serverMetricId, Instant.parse("2026-08-08T10:00:00Z"), "node-1", 42.5, 1024L)
      val logEntry      = Event.LogEntry(logEntryId, Instant.parse("2026-08-08T10:01:00Z"), "node-2", LogLevel.Error, "boom")
      val customMetric  = Event.CustomMetric(customMetricId, Instant.parse("2026-08-08T10:02:00Z"), "node-3", "queue_depth", 7.0, Map("region" -> "eu-west-1"))

      for
        _                 <- Migrations.migrate[IO](config)
        _                 <- repo.insert(serverMetric)
        _                 <- repo.insert(logEntry)
        _                 <- repo.insert(customMetric)
        foundServerMetric <- repo.findById(serverMetricId)
        notFound          <- repo.findById(EventId.random())
        all               <- repo.list()
      yield
        assertEquals(foundServerMetric, Some(serverMetric))
        assertEquals(notFound, None)
        assertEquals(all.toSet, Set(serverMetric, logEntry, customMetric))
    finally container.stop()
  }
