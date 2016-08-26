package com.github.jeroenr

import akka.actor.{Actor, ActorLogging, Props}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives
import spray.json.DefaultJsonProtocol
import akka.pattern._
import akka.util.Timeout
import com.github.jeroenr.RouteManager._

trait Protocols extends DefaultJsonProtocol with SprayJsonSupport {
  implicit val serviceRoute = jsonFormat3(ServiceRoute)
  implicit val registerServiceRoute = jsonFormat4(RegisterServiceRoute)
}

trait ApiDashboardService extends Directives with Protocols {
  self: RouteRepository =>

  import scala.concurrent.duration._

  private lazy val routeManager = system.actorOf(Props(new RouteManager(this)))

  implicit val timeout = Timeout(5 seconds)

  val dashboardRoute =
    pathPrefix("services") {
      pathEndOrSingleSlash {
        get {
          complete {
            (routeManager ? ListServices)
              .mapTo[Iterable[ServiceRoute]]
              .map(services =>
                Map("services" -> services)
              )
          }
        } ~
          post {
            entity(as[RegisterServiceRoute]) { case RegisterServiceRoute(name, host, resource, maybePort) =>
              complete {
                routeManager ! AddServiceRoute(name, host, resource, maybePort)
                StatusCodes.Accepted
              }
            }
          }
      } ~
      path(Segment) { resource =>
        rejectEmptyResponse {
          complete {
            (routeManager ? GetServiceByResource(resource)).mapTo[Option[ServiceRoute]]
          }
        }
      }
    }
}

class RouteManager(routeRepository: RouteRepository) extends Actor with ActorLogging {
  import RouteManager._
  override def receive: Receive = {
    case ListServices =>
      sender() ! routeRepository.serviceRoutes()
    case GetServiceByResource(resource) =>
      sender() ! routeRepository.serviceRoute(resource)
    case AddServiceRoute(name, host, resource, maybePort) =>
      val port = maybePort.getOrElse(80)
      log.info(s"Adding route for resource $resource on host $host:$port")
      routeRepository.addRoute(resource, host, port)
  }
}

object RouteManager {
  case object ListServices
  case class GetServiceByResource(resource: String)
  case class AddServiceRoute(name: String, host: String, resource: String, port: Option[Int] = None)
}

case class ServiceRoute(resource: String, host: String, port: Int)

case class RegisterServiceRoute(name: String, host: String, resource: String, port: Option[Int] = None)
