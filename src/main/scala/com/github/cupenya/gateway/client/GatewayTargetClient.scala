package com.github.cupenya.gateway.client

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server.{ RequestContext, Route }
import akka.stream.Materializer
import akka.stream.scaladsl.{ Sink, Source }
import com.github.cupenya.gateway.{ Config, Logging }

import scala.concurrent.ExecutionContext

class GatewayTargetClient(val host: String, val port: Int, secured: Boolean)(
    implicit
    val system: ActorSystem, ec: ExecutionContext, materializer: Materializer
) extends Logging {
  private val connector = Http(system).outgoingConnection(host, port)

  private val authClient = new AuthServiceClient(
    Config.integration.authentication.host,
    Config.integration.authentication.port
  )

  private val STANDARD_PORTS = List(80, 443)
  private val API_PREFIX = Config.gateway.prefix

  val route = Route { context =>
    val request = context.request
    val originalHeaders = request.headers.toList
    val filteredHeaders = (hostHeader :: originalHeaders - Host).noEmptyHeaders
    val eventualProxyResponse = if (secured) {
      log.debug(s"Need token for request ${request.uri.path}")
      authClient.getToken(filteredHeaders).flatMap {
        case Right(tokenResponse) =>
          log.debug(s"Token ${tokenResponse.jwt}")
          val headersWithAuth = Authorization(OAuth2BearerToken(tokenResponse.jwt)) :: filteredHeaders
          proxyRequest(context, request, headersWithAuth)
        case Left(errorResponse) =>
          log.warn(s"Failed to retrieve token.")
          context.complete(errorResponse)
      }
    } else {
      proxyRequest(context, request, filteredHeaders)
    }
    eventualProxyResponse.transform(
      identity,
      t => {
        log.error("Error while proxying request", t)
        t
      }
    )
  }

  private def proxyRequest(context: RequestContext, request: HttpRequest, headers: List[HttpHeader]) = {
    val proxiedRequest = context.request.copy(
      uri = createProxiedUri(context, request.uri),
      headers = headers
    )
    log.debug(s"Proxying request: $proxiedRequest")
    Source.single(proxiedRequest)
      .via(connector)
      .runWith(Sink.head)
      .flatMap(context.complete(_))
  }

  private def hostHeader: Host =
    if (isStandardPort) Host(host) else Host(host, port)

  private def createProxiedUri(ctx: RequestContext, originalUri: Uri): Uri = {
    val uri = originalUri
      .withHost(host)
      .withPath(originalUri.path.dropChars(API_PREFIX.length + 1))
      .withQuery(originalUri.query())
    if (isStandardPort) uri.withPort(0) else uri.withPort(port)
  }

  private def isStandardPort =
    STANDARD_PORTS.contains(port)
}
