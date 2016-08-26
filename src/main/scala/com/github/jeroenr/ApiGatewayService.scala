package com.github.jeroenr

import java.util.concurrent.atomic.AtomicReference

import akka.actor.{Actor, ActorLogging, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server.{Directives, RequestContext, Route}
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}

import scala.concurrent.ExecutionContext

class ApiGatewayService()(implicit val system: ActorSystem, ec: ExecutionContext, materializer: Materializer) extends Config with Directives {
  private val routeHolder = new AtomicReference[Route](reject)

  private def currentRoute(): Route = routeHolder.get()

  private def addRoute(route: Route): Unit =
    routeHolder.set(routeHolder.get() ~ route)

  def addRoute(resource: String, host: String, port: Int): Unit =
    addRoute(pathPrefix(resource) { ctx =>
      new ProxyRoute(host, port).route(ctx)
    })

  val gatewayRoute = (ctx: RequestContext) => currentRoute()(ctx)

}

class RouteManager(apiGatewayService: ApiGatewayService) extends Actor with ActorLogging {
  import RouteManager._
  override def receive: Receive = {
    case AddServiceRoute(name, host, resource, maybePort) =>
      val port = maybePort.getOrElse(80)
      log.info(s"Adding route for resource $resource on host $host:$port")
      apiGatewayService.addRoute(resource, host, port)
  }
}

object RouteManager {
  case class AddServiceRoute(name: String, host: String, resource: String, port: Option[Int] = None)
}

class ProxyRoute(host: String, port: Int)(implicit val system: ActorSystem, ec: ExecutionContext, materializer: Materializer ) extends Config with Logging {
  val connector = Http(system).outgoingConnection(host, port)

  val route = Route { context =>
    val request = context.request
    val originalHeaders = request.headers.toList
    val proxiedRequest = context.request.copy(
      uri = createProxiedUri(context, request.uri),
      headers = (hostHeader :: originalHeaders - Host).noEmptyHeaders
    )
    log.info(s"Proxying request: $proxiedRequest")
    Source.single(proxiedRequest)
      .via(connector)
      .runWith(Sink.head)
      .flatMap(context.complete(_))
  }

  private def hostHeader: Host =
    if (port == 80 || port == 443) Host(host) else Host(host, port)

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
