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
import java.util.UUID
import com.adrian.eventmetrics.domain.model.{Event, LogLevel}
import com.adrian.eventmetrics.infrastructure.db.{DatabaseConfig, Migrations}
import com.adrian.eventmetrics.infrastructure.persistence.DoobieEventRepository
import EventJson.given

class EventsApiSpec extends CatsEffectSuite:

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
      val requestJson =
        (CreateEventRequest.LogEntry("integration-test", LogLevel.Warn, "disk usage high"): CreateEventRequest).asJson

      for
        _        <- Migrations.migrate[IO](config)
        repo      = new DoobieEventRepository[IO](xa)
        app       = HttpApi.routes[IO](repo).orNotFound
        postResp <- app.run(Request[IO](Method.POST, uri"/events").withEntity(requestJson))
        postBody <- postResp.as[String]
        created   = decode[Event](postBody).getOrElse(fail(s"could not decode created event: $postBody"))
        createdId = idOf(created)
        getResp  <- app.run(Request[IO](Method.GET, Uri.unsafeFromString(s"/events/$createdId")))
        getBody  <- getResp.as[String]
        listResp <- app.run(Request[IO](Method.GET, uri"/events"))
        listBody <- listResp.as[String]
        missing  <- app.run(Request[IO](Method.GET, Uri.unsafeFromString(s"/events/${UUID.randomUUID()}")))
      yield
        assertEquals(postResp.status, Status.Created)
        assertEquals(getResp.status, Status.Ok)
        assertEquals(decode[Event](getBody), Right(created))
        assertEquals(decode[List[Event]](listBody), Right(List(created)))
        assertEquals(missing.status, Status.NotFound)
    finally container.stop()
  }

  private def idOf(event: Event): UUID = event match
    case Event.ServerMetric(id, _, _, _, _)    => id.value
    case Event.LogEntry(id, _, _, _, _)        => id.value
    case Event.CustomMetric(id, _, _, _, _, _) => id.value
