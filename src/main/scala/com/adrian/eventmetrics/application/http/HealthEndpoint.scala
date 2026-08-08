package com.adrian.eventmetrics.application.http

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir.*
import sttp.tapir.json.circe.*

final case class HealthStatus(status: String)

object HealthStatus:
  given Codec[HealthStatus] = deriveCodec
  given Schema[HealthStatus] = Schema.derived

object HealthEndpoint:
  val health: PublicEndpoint[Unit, Unit, HealthStatus, Any] =
    endpoint.get
      .in("health")
      .out(jsonBody[HealthStatus])
      .description("Health check endpoint")
