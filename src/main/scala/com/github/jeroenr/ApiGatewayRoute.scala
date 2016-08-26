package com.github.jeroenr

import java.util.concurrent.atomic.AtomicReference

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server.{Directives, RequestContext, Route}
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}

import scala.concurrent.ExecutionContext

trait RouteRepository extends Directives {
  implicit val system: ActorSystem

  implicit def ec: ExecutionContext

  implicit def materializer: Materializer

  private val routeHolder = new AtomicReference[Route](reject)

  // TODO: persistence
  private var resourceToRoute = Map.empty[String, ProxyRoute]

  private def createRoute(resource: String, proxyRoute: ProxyRoute) =
    pathPrefix(resource)(proxyRoute.route.apply)

  private def updatedRoutes(): Unit =
    routeHolder.set(resourceToRoute.foldLeft[Route](reject) {
      case (accumulated, (resource, proxyRoute)) => createRoute(resource, proxyRoute) ~ accumulated
    })

  def serviceRoute(resource: String): Option[ServiceRoute] =
    resourceToRoute.get(resource).map(pr => ServiceRoute(resource, pr.host, pr.port))

  def serviceRoutes(): List[ServiceRoute] =
    resourceToRoute.map {
      case (resource, proxyRoute) => ServiceRoute(resource, proxyRoute.host, proxyRoute.port)
    }.toList

  def addRoute(resource: String, host: String, port: Int): Unit = {
    resourceToRoute = resourceToRoute.updated(resource, new ProxyRoute(host, port))
    updatedRoutes()
  }

  def currentRoute(): Route = routeHolder.get()

}

trait ApiGatewayRoute extends Config {
  self: RouteRepository =>

  val gatewayRoute = (ctx: RequestContext) => currentRoute()(ctx)
}

class ProxyRoute(val host: String, val port: Int)(
  implicit val system: ActorSystem, ec: ExecutionContext, materializer: Materializer
) extends Config with Logging {
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
