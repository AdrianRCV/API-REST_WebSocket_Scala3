package com.adrian.eventmetrics.application.realtime

import cats.effect.IO
import munit.CatsEffectSuite
import scala.concurrent.duration.*
import java.time.Instant
import com.adrian.eventmetrics.domain.model.{Event, EventId, LogLevel}

class EventBroadcasterSpec extends CatsEffectSuite:

  test("a subscriber receives an event published after it subscribed") {
    val event = Event.ServerMetric(EventId.random(), Instant.parse("2026-08-10T10:00:00Z"), "node-1", 10.0, 512L)

    for
      broadcaster <- EventBroadcaster[IO]
      fiber       <- broadcaster.subscribe.take(1).compile.lastOrError.start
      _           <- IO.sleep(200.millis) // deja que la suscripción se registre antes de publicar
      _           <- broadcaster.publish(event)
      got         <- fiber.joinWithNever
    yield assertEquals(got, event)
  }

  test("a subscriber receives multiple published events in order") {
    val e1 = Event.LogEntry(EventId.random(), Instant.parse("2026-08-10T10:01:00Z"), "node-2", LogLevel.Info, "first")
    val e2 = Event.LogEntry(EventId.random(), Instant.parse("2026-08-10T10:02:00Z"), "node-2", LogLevel.Info, "second")

    for
      broadcaster <- EventBroadcaster[IO]
      fiber       <- broadcaster.subscribe.take(2).compile.toList.start
      _           <- IO.sleep(200.millis)
      _           <- broadcaster.publish(e1)
      _           <- broadcaster.publish(e2)
      got         <- fiber.joinWithNever
    yield assertEquals(got, List(e1, e2))
  }
