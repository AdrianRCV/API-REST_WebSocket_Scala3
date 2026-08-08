package com.adrian.eventmetrics.application.http

import munit.FunSuite
import java.time.Instant
import com.adrian.eventmetrics.domain.model.{Event, EventId, LogLevel}

class EventsEndpointSpec extends FunSuite:

  test("CreateEventRequest.toDomain maps ServerMetric fields and injects id/timestamp") {
    val id = EventId.random()
    val ts = Instant.parse("2026-08-08T12:00:00Z")
    val request = CreateEventRequest.ServerMetric("node-1", 42.5, 1024L)

    assertEquals(
      CreateEventRequest.toDomain(request, id, ts),
      Event.ServerMetric(id, ts, "node-1", 42.5, 1024L)
    )
  }

  test("CreateEventRequest.toDomain maps LogEntry fields and injects id/timestamp") {
    val id = EventId.random()
    val ts = Instant.parse("2026-08-08T12:01:00Z")
    val request = CreateEventRequest.LogEntry("node-2", LogLevel.Warn, "disk usage high")

    assertEquals(
      CreateEventRequest.toDomain(request, id, ts),
      Event.LogEntry(id, ts, "node-2", LogLevel.Warn, "disk usage high")
    )
  }

  test("CreateEventRequest.toDomain maps CustomMetric fields and injects id/timestamp") {
    val id = EventId.random()
    val ts = Instant.parse("2026-08-08T12:02:00Z")
    val request = CreateEventRequest.CustomMetric("node-3", "queue_depth", 7.0, Map("region" -> "eu-west-1"))

    assertEquals(
      CreateEventRequest.toDomain(request, id, ts),
      Event.CustomMetric(id, ts, "node-3", "queue_depth", 7.0, Map("region" -> "eu-west-1"))
    )
  }
