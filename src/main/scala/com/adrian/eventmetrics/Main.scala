package com.adrian.eventmetrics

import cats.effect.{ExitCode, IO, IOApp}
import com.comcast.ip4s.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits.*
import org.typelevel.doobie.Transactor
import com.adrian.eventmetrics.application.http.HttpApi
import com.adrian.eventmetrics.infrastructure.db.DatabaseConfig
import com.adrian.eventmetrics.infrastructure.persistence.DoobieEventRepository

object Main extends IOApp:

  def run(args: List[String]): IO[ExitCode] =
    for
      config <- DatabaseConfig.fromEnv[IO]
      xa = Transactor.fromDriverManager[IO](
        driver = "org.postgresql.Driver",
        url = config.jdbcUrl,
        user = config.user,
        password = config.password,
        logHandler = None
      )
      repo      = new DoobieEventRepository[IO](xa)
      app       = HttpApi.routes[IO](repo).orNotFound
      exitCode <- EmberServerBuilder
                    .default[IO]
                    .withHost(host"0.0.0.0")
                    .withPort(port"8080")
                    .withHttpApp(app)
                    .build
                    .use(_ => IO.never)
                    .as(ExitCode.Success)
    yield exitCode
