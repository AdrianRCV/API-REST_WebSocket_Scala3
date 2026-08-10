package com.adrian.eventmetrics.infrastructure.persistence

import cats.effect.Async
import cats.syntax.all.*
import org.typelevel.doobie.*
import org.typelevel.doobie.implicits.*
import org.typelevel.doobie.postgres.implicits.*
import org.typelevel.doobie.postgres.circe.jsonb.implicits.*
import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.*
import java.time.Instant
import java.util.UUID
import com.adrian.eventmetrics.domain.model.{Event, EventId, LogLevel}
import com.adrian.eventmetrics.domain.repository.EventRepository

class DoobieEventRepository[F[_]: Async](xa: Transactor[F]) extends EventRepository[F]:
  import DoobieEventRepository.*

  def insert(event: Event): F[Unit] =
    val (id, eventType, timestamp, source, payload) = toRow(event)
    sql"""
      INSERT INTO events (id, event_type, occurred_at, source, payload)
      VALUES ($id, $eventType, $timestamp, $source, $payload)
    """.update.run.transact(xa).void

  def findById(id: EventId): F[Option[Event]] =
    sql"""
      SELECT id, event_type, occurred_at, source, payload
      FROM events
      WHERE id = ${id.value}
    """.query[Row].option.transact(xa).flatMap {
      case None      => Async[F].pure(None)
      case Some(row) => Async[F].fromEither(fromRow(row)).map(Some(_))
    }

  def list(): F[List[Event]] =
    sql"""
      SELECT id, event_type, occurred_at, source, payload
      FROM events
      ORDER BY occurred_at, id
    """.query[Row].to[List].transact(xa).flatMap { rows =>
      rows.traverse(row => Async[F].fromEither(fromRow(row)))
    }

object DoobieEventRepository:

  // Storage-side codecs below are deliberately independent of `EventsEndpoint.scala`'s
  // `EventJson` wire-format codecs — do not deduplicate them, or the on-disk format and the
  // public API contract would become coupled and unable to evolve separately.
  private given Encoder[LogLevel] = Encoder.encodeString.contramap(_.toString)
  private given Decoder[LogLevel] = Decoder.decodeString.emap { s =>
    LogLevel.values.find(_.toString == s).toRight(s"Unknown LogLevel: $s")
  }

  private final case class ServerMetricPayload(cpuUsagePct: Double, memoryUsageMb: Long)
  private object ServerMetricPayload:
    given Encoder[ServerMetricPayload] = deriveEncoder
    given Decoder[ServerMetricPayload] = deriveDecoder

  private final case class LogEntryPayload(level: LogLevel, message: String)
  private object LogEntryPayload:
    given Encoder[LogEntryPayload] = deriveEncoder
    given Decoder[LogEntryPayload] = deriveDecoder

  private final case class CustomMetricPayload(name: String, value: Double, tags: Map[String, String])
  private object CustomMetricPayload:
    given Encoder[CustomMetricPayload] = deriveEncoder
    given Decoder[CustomMetricPayload] = deriveDecoder

  private final case class Row(id: UUID, eventType: String, occurredAt: Instant, source: String, payload: Json)

  private def toRow(event: Event): (UUID, String, Instant, String, Json) =
    event match
      case Event.ServerMetric(id, timestamp, source, cpuUsagePct, memoryUsageMb) =>
        (id.value, "server_metric", timestamp, source, ServerMetricPayload(cpuUsagePct, memoryUsageMb).asJson)
      case Event.LogEntry(id, timestamp, source, level, message) =>
        (id.value, "log_entry", timestamp, source, LogEntryPayload(level, message).asJson)
      case Event.CustomMetric(id, timestamp, source, name, value, tags) =>
        (id.value, "custom_metric", timestamp, source, CustomMetricPayload(name, value, tags).asJson)

  private def fromRow(row: Row): Either[Throwable, Event] =
    row.eventType match
      case "server_metric" =>
        row.payload.as[ServerMetricPayload].leftWiden[Throwable].map { p =>
          Event.ServerMetric(EventId(row.id), row.occurredAt, row.source, p.cpuUsagePct, p.memoryUsageMb)
        }
      case "log_entry" =>
        row.payload.as[LogEntryPayload].leftWiden[Throwable].map { p =>
          Event.LogEntry(EventId(row.id), row.occurredAt, row.source, p.level, p.message)
        }
      case "custom_metric" =>
        row.payload.as[CustomMetricPayload].leftWiden[Throwable].map { p =>
          Event.CustomMetric(EventId(row.id), row.occurredAt, row.source, p.name, p.value, p.tags)
        }
      case other =>
        Left(new IllegalStateException(s"Unknown event_type in database: $other"))
