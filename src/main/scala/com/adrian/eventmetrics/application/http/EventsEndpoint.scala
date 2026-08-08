package com.adrian.eventmetrics.application.http

import io.circe.{Codec, Decoder, Encoder}
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir.*
import sttp.tapir.json.circe.*
import sttp.tapir.generic.auto.*
import sttp.model.StatusCode
import java.time.Instant
import java.util.UUID
import com.adrian.eventmetrics.domain.model.{Event, EventId, LogLevel}

/** JSON (de)serialization for the public HTTP API — separate from the circe codecs
  * `DoobieEventRepository` uses for the JSONB storage payload. The API wire format and
  * the storage format are deliberately allowed to evolve independently (see design doc).
  */
object EventJson:
  given Encoder[EventId] = Encoder.encodeUUID.contramap(_.value)
  given Decoder[EventId] = Decoder.decodeUUID.map(EventId.apply)
  given Schema[EventId] = Schema.string

  given Encoder[Instant] = Encoder.encodeString.contramap(_.toString)
  given Decoder[Instant] = Decoder.decodeString.emap { s =>
    scala.util.Try(Instant.parse(s)).toEither.left.map(_ => s"Invalid Instant: $s")
  }

  given Encoder[LogLevel] = Encoder.encodeString.contramap(_.toString)
  given Decoder[LogLevel] = Decoder.decodeString.emap { s =>
    LogLevel.values.find(_.toString == s).toRight(s"Unknown LogLevel: $s")
  }
  given Schema[LogLevel] = Schema.derivedEnumeration.defaultStringBased

  given Codec[Event] = deriveCodec
  // `Event` is encoded by circe's default coproduct codec as a single-field wrapper object,
  // e.g. {"ServerMetric": {"id": ..., "timestamp": ..., ...}}. `Schema.oneOfWrapped` documents
  // that same wrapped shape (rather than a flat, un-wrapped oneOf), so the generated OpenAPI
  // schema matches what circe actually puts on the wire.
  given Schema[Event] = Schema.oneOfWrapped[Event]

enum CreateEventRequest:
  case ServerMetric(source: String, cpuUsagePct: Double, memoryUsageMb: Long)
  case LogEntry(source: String, level: LogLevel, message: String)
  case CustomMetric(source: String, name: String, value: Double, tags: Map[String, String])

object CreateEventRequest:
  import EventJson.given
  given Codec[CreateEventRequest] = deriveCodec
  // Same rationale as `Schema[Event]` above: match circe's wrapped coproduct wire format.
  given Schema[CreateEventRequest] = Schema.oneOfWrapped[CreateEventRequest]

  def toDomain(request: CreateEventRequest, id: EventId, timestamp: Instant): Event =
    request match
      case ServerMetric(source, cpu, mem)          => Event.ServerMetric(id, timestamp, source, cpu, mem)
      case LogEntry(source, level, message)        => Event.LogEntry(id, timestamp, source, level, message)
      case CustomMetric(source, name, value, tags) => Event.CustomMetric(id, timestamp, source, name, value, tags)

object EventsEndpoint:
  import EventJson.given

  val create: PublicEndpoint[CreateEventRequest, Unit, Event, Any] =
    endpoint.post
      .in("events")
      .in(jsonBody[CreateEventRequest])
      .out(statusCode(StatusCode.Created).and(jsonBody[Event]))

  val list: PublicEndpoint[Unit, Unit, List[Event], Any] =
    endpoint.get
      .in("events")
      .out(jsonBody[List[Event]])

  val getById: PublicEndpoint[UUID, Unit, Event, Any] =
    endpoint.get
      .in("events" / path[UUID]("id"))
      .out(jsonBody[Event])
      .errorOut(statusCode(StatusCode.NotFound))
