package com.adrian.eventmetrics.application.http

import cats.effect.{Async, Clock}
import cats.syntax.all.*
import io.circe.syntax.*
import org.http4s.HttpRoutes
import org.http4s.server.websocket.WebSocketBuilder2
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
    val streamServerEndpoint: ServerEndpoint[Fs2Streams[F] & sttp.capabilities.WebSockets, F] =
      EventsWsEndpoint.stream[F].serverLogicSuccess { _ =>
        val pipe: fs2.Pipe[F, WebSocketFrame, WebSocketFrame] =
          (_: fs2.Stream[F, WebSocketFrame]) =>
            broadcaster.subscribe.map(event => WebSocketFrame.text(event.asJson.noSpaces))
        Async[F].pure(pipe)
      }

    Http4sServerInterpreter[F]().toWebSocketRoutes(streamServerEndpoint)(wsb)
