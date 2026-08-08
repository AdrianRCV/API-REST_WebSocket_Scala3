package com.adrian.eventmetrics.domain.model

import java.time.Instant
import java.util.UUID

opaque type EventId = UUID

object EventId:
  def apply(uuid: UUID): EventId = uuid
  def random(): EventId = UUID.randomUUID()

  extension (id: EventId) def value: UUID = id

enum LogLevel:
  case Debug, Info, Warn, Error

enum Event:
  case ServerMetric(
    id: EventId,
    timestamp: Instant,
    source: String,
    cpuUsagePct: Double,
    memoryUsageMb: Long
  )
  case LogEntry(
    id: EventId,
    timestamp: Instant,
    source: String,
    level: LogLevel,
    message: String
  )
  case CustomMetric(
    id: EventId,
    timestamp: Instant,
    source: String,
    name: String,
    value: Double,
    tags: Map[String, String]
  )
