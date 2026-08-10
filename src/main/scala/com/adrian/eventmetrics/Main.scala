package com.adrian.eventmetrics

import cats.effect.{ExitCode, IO, IOApp, Resource}
import cats.syntax.semigroupk.*
import com.comcast.ip4s.*
import org.typelevel.doobie.hikari.HikariTransactor
import org.typelevel.doobie.util.ExecutionContexts
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits.*
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
      config      <- DatabaseConfig.fromEnv[IO]
      _           <- Migrations.migrate[IO](config)
      broadcaster <- EventBroadcaster[IO]
      exit        <- transactor(config).use { xa =>
                       val repo = new DoobieEventRepository[IO](xa)

                       EmberServerBuilder
                         .default[IO]
                         .withHost(host"0.0.0.0")
                         .withPort(port"8080")
                         .withHttpWebSocketApp { wsb =>
                           // `wsRoutes` is tried first: its path is the fixed literal segment "events" / "stream",
                           // but `routes` also exposes `GET /events/{id}` with a UUID path capture. If `routes` were
                           // tried first, that capture would match "stream" as the `id` segment, fail to decode it as
                           // a UUID, and tapir's default decode-failure handler returns a 400 directly instead of
                           // falling through — so `wsRoutes` would never be reached. Trying `wsRoutes` first avoids
                           // that conflict; it only matches the exact `/events/stream` path with a WS upgrade.
                           (HttpApi.wsRoutes[IO](broadcaster)(wsb) <+> HttpApi.routes[IO](repo, broadcaster)).orNotFound
                         }
                         .build
                         .use(_ => IO.never)
                         .as(ExitCode.Success)
                     }
    yield exit
