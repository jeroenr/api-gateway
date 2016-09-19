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

class GatewayTargetClient(val host: String, val port: Int)(
    implicit
    val system: ActorSystem, ec: ExecutionContext, materializer: Materializer
) extends Logging {
  val connector = Http(system).outgoingConnection(host, port)

  val route = Route { context =>
    val request = context.request
    val originalHeaders = request.headers.toList
    val proxiedRequest = context.request.copy(
      uri = createProxiedUri(context, request.uri),
      headers = (hostHeader :: originalHeaders - Host).noEmptyHeaders
    )
    log.info(s"Proxying request: $proxiedRequest")
    /*
      TODO: authentication
      1) get JWT token from auth service
      2) if successful pass on in request header
      3) if unsuccessful (expired) forward 401 response
      */
//    Source.single()

    Source.single(proxiedRequest)
      .via(connector)
      .runWith(Sink.head)
      .flatMap(context.complete(_))
  }

  private def hostHeader: Host = Host(host, port)

  private def createProxiedUri(ctx: RequestContext, originalUri: Uri): Uri =
    originalUri
      .withHost(host)
      .withPort(port)
      .withPath(originalUri.path)
      .withQuery(originalUri.query())

  implicit class RichHeaders(headers: List[HttpHeader]) {

    def noEmptyHeaders: List[HttpHeader] =
      headers.filterNot(_.value.isEmpty)

    def valueOf[T](header: ModeledCompanion[T]): Option[String] =
      headers.find(_.is(`Remote-Address`.lowercaseName)).map(_.value)

    def -[T](header: ModeledCompanion[T]): List[HttpHeader] =
      headers.filterNot(_.is(header.lowercaseName))
  }

}
