package com.adrian.eventmetrics.application.http

import cats.effect.IO
import com.dimafeng.testcontainers.PostgreSQLContainer
import org.typelevel.doobie.Transactor
import io.circe.parser.decode
import io.circe.syntax.*
import munit.CatsEffectSuite
import org.http4s.*
import org.http4s.circe.*
import org.http4s.implicits.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import java.util.UUID
import com.adrian.eventmetrics.application.realtime.EventBroadcaster
import com.adrian.eventmetrics.domain.model.{Event, LogLevel}
import com.adrian.eventmetrics.infrastructure.db.{DatabaseConfig, Migrations}
import com.adrian.eventmetrics.infrastructure.persistence.DoobieEventRepository
import EventJson.given
import scala.concurrent.duration.*

class EventsApiSpec extends CatsEffectSuite:

  // CI runners can take well over munit-cats-effect's default 30s munitIOTimeout for the
  // Postgres testcontainer to actually accept connections (container "started" fires before
  // Postgres finishes its real startup). Override the timeout that actually governs IO
  // cancellation per the deprecation note on munitTimeout in CatsEffectSuite.
  override def munitIOTimeout: Duration = 2.minutes

  private given Logger[IO] = Slf4jLogger.getLogger[IO]

  test("POST /events then GET /events and GET /events/{id} round-trip against a real database") {
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

      val logEntryRequest: CreateEventRequest =
        CreateEventRequest.LogEntry("integration-test", LogLevel.Warn, "disk usage high")
      // memoryUsageMb above 2^53 (9007199254740992): if the wire codec ever round-trips
      // through a Double, this value loses precision. Proves the Long survives intact.
      val serverMetricRequest: CreateEventRequest =
        CreateEventRequest.ServerMetric("integration-test", 87.5, 9007199254740993L)
      val customMetricRequest: CreateEventRequest =
        CreateEventRequest.CustomMetric(
          "integration-test",
          "queue_depth",
          7.0,
          Map("region" -> "eu-west-1", "team" -> "platform")
        )

      def postAndGet(app: org.http4s.HttpApp[IO], request: CreateEventRequest): IO[Event] =
        for
          postResp <- app.run(Request[IO](Method.POST, uri"/events").withEntity(request.asJson))
          postBody <- postResp.as[String]
          _         = assertEquals(postResp.status, Status.Created)
          created   = decode[Event](postBody).getOrElse(fail(s"could not decode created event: $postBody"))
          createdId = idOf(created)
          getResp  <- app.run(Request[IO](Method.GET, Uri.unsafeFromString(s"/events/$createdId")))
          getBody  <- getResp.as[String]
          _         = assertEquals(getResp.status, Status.Ok)
          _         = assertEquals(decode[Event](getBody), Right(created))
        yield created

      for
        _              <- Migrations.migrate[IO](config)
        repo            = new DoobieEventRepository[IO](xa)
        broadcaster    <- EventBroadcaster[IO]
        app             = HttpApi.routes[IO](repo, broadcaster).orNotFound
        createdLogEntry     <- postAndGet(app, logEntryRequest)
        createdServerMetric <- postAndGet(app, serverMetricRequest)
        createdCustomMetric <- postAndGet(app, customMetricRequest)
        listResp       <- app.run(Request[IO](Method.GET, uri"/events"))
        listBody       <- listResp.as[String]
        missing        <- app.run(Request[IO](Method.GET, Uri.unsafeFromString(s"/events/${UUID.randomUUID()}")))
      yield
        assertEquals(
          decode[List[Event]](listBody).map(_.toSet),
          Right(Set(createdLogEntry, createdServerMetric, createdCustomMetric))
        )
        assertEquals(missing.status, Status.NotFound)
    finally container.stop()
  }

  private def idOf(event: Event): UUID = event match
    case Event.ServerMetric(id, _, _, _, _)    => id.value
    case Event.LogEntry(id, _, _, _, _)        => id.value
    case Event.CustomMetric(id, _, _, _, _, _) => id.value
