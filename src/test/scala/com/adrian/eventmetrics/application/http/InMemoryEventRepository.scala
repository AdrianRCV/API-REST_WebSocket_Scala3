package com.adrian.eventmetrics.application.http

import cats.effect.{Ref, Sync}
import cats.syntax.all.*
import com.adrian.eventmetrics.domain.model.{Event, EventId}
import com.adrian.eventmetrics.domain.repository.EventRepository

final class InMemoryEventRepository[F[_]: Sync](state: Ref[F, Map[EventId, Event]]) extends EventRepository[F]:

  def insert(event: Event): F[Unit] =
    val id = event match
      case Event.ServerMetric(id, _, _, _, _)    => id
      case Event.LogEntry(id, _, _, _, _)        => id
      case Event.CustomMetric(id, _, _, _, _, _) => id
    state.update(_.updated(id, event))

  def findById(id: EventId): F[Option[Event]] =
    state.get.map(_.get(id))

  def list(): F[List[Event]] =
    state.get.map(_.values.toList)

object InMemoryEventRepository:
  def empty[F[_]: Sync]: F[InMemoryEventRepository[F]] =
    Ref.of[F, Map[EventId, Event]](Map.empty).map(new InMemoryEventRepository(_))
