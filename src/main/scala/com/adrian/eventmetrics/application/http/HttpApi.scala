package com.adrian.eventmetrics.application.http

import cats.effect.{Async, Clock}
import cats.syntax.all.*
import io.circe.syntax.*
import org.http4s.{HttpApp, HttpRoutes}
import org.http4s.implicits.*
import org.http4s.server.websocket.WebSocketBuilder2
import sttp.capabilities.WebSockets
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.http4s.Http4sServerInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import sttp.ws.WebSocketFrame
import com.adrian.eventmetrics.application.realtime.EventBroadcaster
import com.adrian.eventmetrics.domain.model.EventId
import com.adrian.eventmetrics.domain.repository.EventRepository
import EventJson.given

object HttpApi:

  def routes[F[_]: Async](
      eventRepository: EventRepository[F],
      broadcaster: EventBroadcaster[F]
  ): HttpRoutes[F] =
    val healthServerEndpoint: ServerEndpoint[Any, F] =
      HealthEndpoint.health.serverLogicSuccess(_ => Async[F].pure(HealthStatus("UP")))

    val createEventServerEndpoint: ServerEndpoint[Any, F] =
      EventsEndpoint.create.serverLogicSuccess { request =>
        for
          id        <- Async[F].delay(EventId.random())
          timestamp <- Clock[F].realTimeInstant
          event      = CreateEventRequest.toDomain(request, id, timestamp)
          _         <- eventRepository.insert(event)
          _         <- broadcaster.publish(event)
        yield event
      }

    val listEventsServerEndpoint: ServerEndpoint[Any, F] =
      EventsEndpoint.list.serverLogicSuccess(_ => eventRepository.list())

    val getEventByIdServerEndpoint: ServerEndpoint[Any, F] =
      EventsEndpoint.getById.serverLogic { rawId =>
        eventRepository.findById(EventId(rawId)).map {
          case Some(event) => Right(event)
          case None         => Left(())
        }
      }

    val apiEndpoints = List(
      healthServerEndpoint,
      createEventServerEndpoint,
      listEventsServerEndpoint,
      getEventByIdServerEndpoint
    )
    val docsEndpoints: List[ServerEndpoint[Any, F]] =
      SwaggerInterpreter().fromServerEndpoints[F](apiEndpoints, "event-metrics-system", "0.1.0")

    Http4sServerInterpreter[F]().toRoutes(apiEndpoints ++ docsEndpoints)

  def wsRoutes[F[_]: Async](broadcaster: EventBroadcaster[F])(wsb: WebSocketBuilder2[F]): HttpRoutes[F] =
    val streamServerEndpoint: ServerEndpoint[Fs2Streams[F] & WebSockets, F] =
      EventsWsEndpoint.stream[F].serverLogicSuccess { _ =>
        val pipe: fs2.Pipe[F, WebSocketFrame, WebSocketFrame] =
          (incoming: fs2.Stream[F, WebSocketFrame]) =>
            // The incoming stream must be actively drained even though we never act on client
            // frames: tapir wires this pipe directly with no background draining fiber (autoPing
            // is disabled for the raw WS body), so without pulling on `incoming` the client's Ping
            // and Close frames are never read, breaking keep-alive and clean shutdown.
            incoming.drain.mergeHaltBoth(
              broadcaster.subscribe.map(event => WebSocketFrame.text(event.asJson.noSpaces))
            )
        Async[F].pure(pipe)
      }

    Http4sServerInterpreter[F]().toWebSocketRoutes(streamServerEndpoint)(wsb)

  /** Combines `wsRoutes` and `routes` into the final app, with `wsRoutes` tried first: its path
    * is the fixed literal segment "events" / "stream", but `routes` also exposes
    * `GET /events/{id}` with a UUID path capture. If `routes` were tried first, that capture
    * would match "stream" as the `id` segment, fail to decode it as a UUID, and tapir's default
    * decode-failure handler would return a 400 directly instead of falling through — so
    * `wsRoutes` would never be reached. Trying `wsRoutes` first avoids that conflict; it only
    * matches the exact `/events/stream` path with a WS upgrade.
    */
  def app[F[_]: Async](
      eventRepository: EventRepository[F],
      broadcaster: EventBroadcaster[F]
  )(wsb: WebSocketBuilder2[F]): HttpApp[F] =
    (wsRoutes[F](broadcaster)(wsb) <+> routes[F](eventRepository, broadcaster)).orNotFound
