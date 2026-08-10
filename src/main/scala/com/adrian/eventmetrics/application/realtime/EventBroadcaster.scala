package com.adrian.eventmetrics.application.realtime

import cats.effect.Concurrent
import cats.syntax.all.*
import fs2.Stream
import fs2.concurrent.Topic
import com.adrian.eventmetrics.domain.model.Event

trait EventBroadcaster[F[_]]:
  def publish(event: Event): F[Unit]
  def subscribe: Stream[F, Event]

object EventBroadcaster:
  def apply[F[_]: Concurrent]: F[EventBroadcaster[F]] =
    Topic[F, Event].map { topic =>
      new EventBroadcaster[F]:
        def publish(event: Event): F[Unit] = topic.publish1(event).void
        def subscribe: Stream[F, Event] = topic.subscribe(maxQueued = 16)
    }
