package com.adrian.eventmetrics.domain.model

import java.time.Instant
import java.util.UUID

class EventSpec extends munit.FunSuite:

  test("ServerMetric carries the fields it was constructed with") {
    val id = EventId.random()
    val ts = Instant.parse("2026-08-08T10:00:00Z")
    val event = Event.ServerMetric(id, ts, "node-1", cpuUsagePct = 42.5, memoryUsageMb = 1024L)

    event match
      case Event.ServerMetric(eid, eTs, source, cpu, mem) =>
        assertEquals(eid, id)
        assertEquals(eTs, ts)
        assertEquals(source, "node-1")
        assertEquals(cpu, 42.5)
        assertEquals(mem, 1024L)
      case other =>
        fail(s"expected ServerMetric, got $other")
  }

  test("two EventIds wrapping the same UUID are equal") {
    val uuid = UUID.randomUUID()
    assertEquals(EventId(uuid), EventId(uuid))
  }

  test("LogEntry carries its log level") {
    val event = Event.LogEntry(EventId.random(), Instant.now(), "node-2", LogLevel.Error, "boom")
    event match
      case Event.LogEntry(_, _, _, level, message) =>
        assertEquals(level, LogLevel.Error)
        assertEquals(message, "boom")
      case other =>
        fail(s"expected LogEntry, got $other")
  }
