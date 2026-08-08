package com.adrian.eventmetrics.domain.repository

import com.adrian.eventmetrics.domain.model.{Event, EventId}

trait EventRepository[F[_]]:
  def insert(event: Event): F[Unit]
  def findById(id: EventId): F[Option[Event]]
  def list(): F[List[Event]]
