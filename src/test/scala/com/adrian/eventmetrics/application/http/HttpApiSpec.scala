package com.adrian.eventmetrics.application.http

import cats.effect.IO
import io.circe.parser.decode
import io.circe.syntax.*
import munit.CatsEffectSuite
import org.http4s.*
import org.http4s.circe.*
import org.http4s.implicits.*
import com.adrian.eventmetrics.application.realtime.EventBroadcaster
import com.adrian.eventmetrics.domain.model.{Event, EventId, LogLevel}
import com.adrian.eventmetrics.domain.repository.EventRepository
import EventJson.given

import scala.concurrent.duration.*

/** Test-only `EventRepository` whose `insert` always fails, used to prove that `POST /events`
  * never publishes to the broadcaster when the insert doesn't succeed (and, combined with
  * `HttpApiSpec`'s "publish on insert" test, that publish only happens strictly after a
  * successful insert — not just with the right content).
  */
final class FailingInsertEventRepository[F[_]](using F: cats.effect.Sync[F]) extends EventRepository[F]:
  def insert(event: Event): F[Unit] = F.raiseError(new RuntimeException("insert always fails in this stub"))
  def findById(id: EventId): F[Option[Event]] = F.pure(None)
  def list(): F[List[Event]] = F.pure(Nil)

class HttpApiSpec extends CatsEffectSuite:

  private val appResource: IO[HttpApp[IO]] =
    for
      repo        <- InMemoryEventRepository.empty[IO]
      broadcaster <- EventBroadcaster[IO]
    yield HttpApi.routes[IO](repo, broadcaster).orNotFound

  test("GET /health returns 200 with status UP") {
    val request = Request[IO](Method.GET, uri"/health")
    for
      app      <- appResource
      response <- app.run(request)
      body     <- response.as[String]
    yield
      assertEquals(response.status, Status.Ok)
      assertEquals(decode[HealthStatus](body), Right(HealthStatus("UP")))
  }

  test("GET /docs redirects to the swagger UI") {
    val request = Request[IO](Method.GET, uri"/docs")
    for
      app      <- appResource
      response <- app.run(request)
    yield assertEquals(response.status, Status.PermanentRedirect)
  }

  test("POST /events publishes the created event to the broadcaster") {
    val requestBody: CreateEventRequest = CreateEventRequest.LogEntry("broadcast-test", LogLevel.Info, "hello")

    for
      repo        <- InMemoryEventRepository.empty[IO]
      broadcaster <- EventBroadcaster[IO]
      app          = HttpApi.routes[IO](repo, broadcaster).orNotFound
      result      <- broadcaster.subscribeAwait.use { subscribed =>
                       for
                         subscriberFiber <- subscribed.take(1).compile.lastOrError.start
                         response        <- app.run(Request[IO](Method.POST, uri"/events").withEntity(requestBody.asJson))
                         createdBody     <- response.as[String]
                         created          = decode[Event](createdBody).getOrElse(fail(s"could not decode created event: $createdBody"))
                         published       <- subscriberFiber.joinWithNever
                       yield (published, created)
                     }
      (published, created) = result
    yield assertEquals(published, created)
  }

  test("POST /events does not publish when the insert fails (publish happens strictly after insert)") {
    val requestBody: CreateEventRequest = CreateEventRequest.LogEntry("failing-insert", LogLevel.Info, "should not publish")

    for
      repo        <- IO.pure(new FailingInsertEventRepository[IO])
      broadcaster <- EventBroadcaster[IO]
      app          = HttpApi.routes[IO](repo, broadcaster).orNotFound
      result      <- broadcaster.subscribeAwait.use { subscribed =>
                       // Bound the subscription to the lifetime of this test instead of racing
                       // against a fixed sleep: `interruptAfter` stops the stream after the
                       // timeout regardless of whether an event ever arrived, so `.compile.last`
                       // yields `None` if nothing was published in time.
                       for
                         subscriberFiber <- subscribed.take(1).interruptAfter(500.millis).compile.last.start
                         response        <- app.run(Request[IO](Method.POST, uri"/events").withEntity(requestBody.asJson))
                         published       <- subscriberFiber.joinWithNever
                       yield (response, published)
                     }
      (response, published) = result
    yield
      assert(!response.status.isSuccess, s"expected a non-2xx response, got ${response.status}")
      assertEquals(published, None)
  }
