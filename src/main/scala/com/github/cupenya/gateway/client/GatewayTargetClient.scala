package com.github.cupenya.gateway.client

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server.{RequestContext, Route}
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import com.github.cupenya.gateway.{Config, Logging}

import scala.concurrent.ExecutionContext

class GatewayTargetClient(val host: String, val port: Int)(
  implicit val system: ActorSystem, ec: ExecutionContext, materializer: Materializer
) extends Logging {
  private val connector = Http(system).outgoingConnection(host, port)

  private val authClient = new AuthServiceClient(
    Config.integration.authentication.host,
    Config.integration.authentication.port
  )

  val route = Route { context =>
    val request = context.request
    val originalHeaders = request.headers.toList
    val filteredHeaders = (hostHeader :: originalHeaders - Host).noEmptyHeaders

    authClient.getToken(filteredHeaders).flatMap {
      case Right(tokenResponse) =>
        log.info(s"Token ${tokenResponse.jwt}")
        val proxiedRequest = context.request.copy(
          uri = createProxiedUri(context, request.uri),
          headers = Authorization(OAuth2BearerToken(tokenResponse.jwt)) :: filteredHeaders
        )
        log.info(s"Proxying request: $proxiedRequest")
        Source.single(proxiedRequest)
          .via(connector)
          .runWith(Sink.head)
          .flatMap(context.complete(_))
      case Left(errorResponse) =>
        log.warn(s"Failed to retrieve token.")
        context.complete(errorResponse)
    }.transform(
      identity,
      t => {
        log.error("Error while proxying request", t)
        t
      }
    )
  }

  private def hostHeader: Host = Host(host, port)

  private def createProxiedUri(ctx: RequestContext, originalUri: Uri): Uri =
    originalUri
      .withHost(host)
      .withPort(port)
      .withPath(originalUri.path)
      .withQuery(originalUri.query())

}
