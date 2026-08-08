package com.adrian.eventmetrics

import cats.effect.{ExitCode, IO, IOApp}
import com.comcast.ip4s.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits.*
import com.adrian.eventmetrics.application.http.HttpApi

object Main extends IOApp:

  def run(args: List[String]): IO[ExitCode] =
    val app = HttpApi.routes[IO].orNotFound

    EmberServerBuilder
      .default[IO]
      .withHost(host"0.0.0.0")
      .withPort(port"8080")
      .withHttpApp(app)
      .build
      .use(_ => IO.never)
      .as(ExitCode.Success)
