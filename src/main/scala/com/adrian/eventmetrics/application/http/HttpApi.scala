package com.adrian.eventmetrics.application.http

import cats.effect.Async
import org.http4s.HttpRoutes
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.http4s.Http4sServerInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter

object HttpApi:

  def routes[F[_]: Async]: HttpRoutes[F] =
    val healthServerEndpoint: ServerEndpoint[Any, F] =
      HealthEndpoint.health.serverLogicSuccess(_ => Async[F].pure(HealthStatus("UP")))

    val apiEndpoints = List(healthServerEndpoint)
    val docsEndpoints: List[ServerEndpoint[Any, F]] =
      SwaggerInterpreter().fromServerEndpoints[F](apiEndpoints, "event-metrics-system", "0.1.0")

    Http4sServerInterpreter[F]().toRoutes(apiEndpoints ++ docsEndpoints)
