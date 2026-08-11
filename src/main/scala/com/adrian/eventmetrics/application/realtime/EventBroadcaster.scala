package com.adrian.eventmetrics.application.realtime

import cats.effect.{Concurrent, Resource}
import cats.syntax.all.*
import fs2.Stream
import fs2.concurrent.Topic
import com.adrian.eventmetrics.domain.model.Event

trait EventBroadcaster[F[_]]:
  def publish(event: Event): F[Unit]
  // `Topic.subscribe` is bounded (see `maxQueued` below), so a stalled or slow subscriber can
  // fill its queue and backpressure `publish`, which in turn can block callers of `publish`
  // (e.g. the `POST /events` handler) — accepted single-instance limitation for now.
  def subscribe: Stream[F, Event]
  // Like `subscribe`, but the subscription is registered on resource acquisition rather than on
  // first pull of the returned stream — useful for tests that need a deterministic
  // "subscribed, now publish" ordering without a racy `IO.sleep`.
  def subscribeAwait: Resource[F, Stream[F, Event]]

object EventBroadcaster:
  def apply[F[_]: Concurrent]: F[EventBroadcaster[F]] =
    Topic[F, Event].map { topic =>
      new EventBroadcaster[F]:
        // `publish1` returns `Either[Topic.Closed, Unit]`, discarded here: harmless today since
        // nothing ever closes the topic, but if a future iteration adds shutdown semantics,
        // publishes after close would fail silently and this should be revisited then.
        def publish(event: Event): F[Unit] = topic.publish1(event).void
        def subscribe: Stream[F, Event] = topic.subscribe(maxQueued = 16)
        def subscribeAwait: Resource[F, Stream[F, Event]] = topic.subscribeAwait(maxQueued = 16)
    }
