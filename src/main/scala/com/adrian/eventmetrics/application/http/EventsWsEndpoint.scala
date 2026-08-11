package com.adrian.eventmetrics.application.http

import sttp.capabilities.WebSockets
import sttp.capabilities.fs2.Fs2Streams
import sttp.tapir.*
import sttp.ws.WebSocketFrame

object EventsWsEndpoint:

  def stream[F[_]]: PublicEndpoint[
    Unit,
    Unit,
    fs2.Pipe[F, WebSocketFrame, WebSocketFrame],
    Fs2Streams[F] & WebSockets
  ] =
    endpoint.get
      .in("events" / "stream")
      .out(webSocketBodyRaw(Fs2Streams[F]))
      .description("Streams newly created events as JSON text frames, server-to-client only")
