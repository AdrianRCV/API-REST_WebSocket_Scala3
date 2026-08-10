package com.adrian.eventmetrics

import cats.effect.{ExitCode, IO, IOApp, Resource}
import com.comcast.ip4s.*
import org.typelevel.doobie.hikari.HikariTransactor
import org.typelevel.doobie.util.ExecutionContexts
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import com.adrian.eventmetrics.application.http.HttpApi
import com.adrian.eventmetrics.application.realtime.EventBroadcaster
import com.adrian.eventmetrics.infrastructure.db.{DatabaseConfig, Migrations}
import com.adrian.eventmetrics.infrastructure.persistence.DoobieEventRepository

object Main extends IOApp:

  private given Logger[IO] = Slf4jLogger.getLogger[IO]

  private def transactor(config: DatabaseConfig): Resource[IO, HikariTransactor[IO]] =
    for
      connectEC <- ExecutionContexts.fixedThreadPool[IO](32)
      xa        <- HikariTransactor.newHikariTransactor[IO](
                     driverClassName = "org.postgresql.Driver",
                     url = config.jdbcUrl,
                     user = config.user,
                     pass = config.password,
                     connectEC = connectEC
                   )
    yield xa

  def run(args: List[String]): IO[ExitCode] =
    for
      config <- DatabaseConfig.fromEnv[IO]
      _      <- Migrations.migrate[IO](config)
      broadcaster <- EventBroadcaster[IO]
      exit   <- transactor(config).use { xa =>
                  val repo = new DoobieEventRepository[IO](xa)
                  val app  = HttpApi.routes[IO](repo, broadcaster).orNotFound

                  EmberServerBuilder
                    .default[IO]
                    .withHost(host"0.0.0.0")
                    .withPort(port"8080")
                    .withHttpApp(app)
                    .build
                    .use(_ => IO.never)
                    .as(ExitCode.Success)
                }
    yield exit
