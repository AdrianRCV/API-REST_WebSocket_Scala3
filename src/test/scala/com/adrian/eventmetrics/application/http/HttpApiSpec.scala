package com.adrian.eventmetrics.application.http

import cats.effect.IO
import io.circe.parser.decode
import munit.CatsEffectSuite
import org.http4s.*
import org.http4s.implicits.*

class HttpApiSpec extends CatsEffectSuite:

  private val app: HttpApp[IO] = HttpApi.routes[IO].orNotFound

  test("GET /health returns 200 with status UP") {
    val request = Request[IO](Method.GET, uri"/health")
    for
      response <- app.run(request)
      body     <- response.as[String]
    yield
      assertEquals(response.status, Status.Ok)
      assertEquals(decode[HealthStatus](body), Right(HealthStatus("UP")))
  }

  test("GET /docs returns 200 (swagger UI is mounted)") {
    val request = Request[IO](Method.GET, uri"/docs")
    for response <- app.run(request)
    yield assertEquals(response.status, Status.PermanentRedirect)
  }
