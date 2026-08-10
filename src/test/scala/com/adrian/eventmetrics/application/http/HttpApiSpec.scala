package com.adrian.eventmetrics.application.http

import cats.effect.IO
import io.circe.parser.decode
import io.circe.syntax.*
import munit.CatsEffectSuite
import org.http4s.*
import org.http4s.circe.*
import org.http4s.implicits.*
import com.adrian.eventmetrics.application.realtime.EventBroadcaster
import com.adrian.eventmetrics.domain.model.{Event, LogLevel}
import EventJson.given

import scala.concurrent.duration.*

class HttpApiSpec extends CatsEffectSuite:

  private val appResource: IO[HttpApp[IO]] =
    for
      repo        <- InMemoryEventRepository.empty[IO]
      broadcaster <- EventBroadcaster[IO]
    yield HttpApi.routes[IO](repo, broadcaster).orNotFound

  test("GET /health returns 200 with status UP") {
    val request = Request[IO](Method.GET, uri"/health")
    for
      app      <- appResource
      response <- app.run(request)
      body     <- response.as[String]
    yield
      assertEquals(response.status, Status.Ok)
      assertEquals(decode[HealthStatus](body), Right(HealthStatus("UP")))
  }

  test("GET /docs redirects to the swagger UI") {
    val request = Request[IO](Method.GET, uri"/docs")
    for
      app      <- appResource
      response <- app.run(request)
    yield assertEquals(response.status, Status.PermanentRedirect)
  }

  test("POST /events publishes the created event to the broadcaster") {
    val requestBody: CreateEventRequest = CreateEventRequest.LogEntry("broadcast-test", LogLevel.Info, "hello")

    for
      repo            <- InMemoryEventRepository.empty[IO]
      broadcaster     <- EventBroadcaster[IO]
      app              = HttpApi.routes[IO](repo, broadcaster).orNotFound
      subscriberFiber <- broadcaster.subscribe.take(1).compile.lastOrError.start
      _               <- IO.sleep(200.millis)
      response        <- app.run(Request[IO](Method.POST, uri"/events").withEntity(requestBody.asJson))
      createdBody     <- response.as[String]
      created          = decode[Event](createdBody).getOrElse(fail(s"could not decode created event: $createdBody"))
      published       <- subscriberFiber.joinWithNever
    yield assertEquals(published, created)
  }
