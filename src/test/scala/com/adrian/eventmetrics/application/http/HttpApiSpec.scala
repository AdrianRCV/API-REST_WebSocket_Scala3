package com.adrian.eventmetrics.application.http

import cats.effect.IO
import io.circe.parser.decode
import munit.CatsEffectSuite
import org.http4s.*
import org.http4s.implicits.*

class HttpApiSpec extends CatsEffectSuite:

  private val appResource: IO[HttpApp[IO]] =
    InMemoryEventRepository.empty[IO].map(repo => HttpApi.routes[IO](repo).orNotFound)

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
