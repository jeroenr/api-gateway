package com.github.jeroenr.gateway.server

import akka.actor.{ ActorRef, ActorSystem }
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives
import akka.stream.Materializer
import akka.util.Timeout
import com.github.jeroenr.gateway.configuration.{ GatewayConfiguration, GatewayConfigurationManagerActor }
import com.github.jeroenr.gateway.model.GatewayTarget
import spray.json.DefaultJsonProtocol
import akka.pattern._
import com.github.jeroenr.gateway.Config

import scala.concurrent.ExecutionContext

trait Protocols extends DefaultJsonProtocol with SprayJsonSupport {
  implicit val serviceRouteFormat = jsonFormat3(ServiceRoute)
  implicit val serviceRoutesFormat = jsonFormat1(ServiceRoutes)
  implicit val registerServiceRouteFormat = jsonFormat5(RegisterServiceRoute)
}

trait ApiDashboardService extends Directives with Protocols {
  import scala.concurrent.duration._
  import scala.language.postfixOps

  implicit val system: ActorSystem

  implicit def ec: ExecutionContext

  implicit val materializer: Materializer

  implicit val timeout: Timeout

  private val DEFAULT_PORT = 80

  val gatewayConfigurationManager: ActorRef

  val dashboardRoute =
    pathPrefix("services") {
      pathEndOrSingleSlash {
        get {
          complete {
            (gatewayConfigurationManager ? GatewayConfigurationManagerActor.GetGatewayConfig).mapTo[GatewayConfiguration].map { currentConfig =>
              ServiceRoutes(currentConfig.targets.map {
                case (key, target) => ServiceRoute(key, target.host, target.port)
              }.toList)
            }
          }
        } ~
          post {
            entity(as[RegisterServiceRoute]) {
              case RegisterServiceRoute(name, host, resource, maybePort, maybeSecured) =>
                complete {
                  gatewayConfigurationManager ! GatewayConfigurationManagerActor.UpsertGatewayTarget(
                    GatewayTarget(resource, host, maybePort.getOrElse(DEFAULT_PORT), maybeSecured.getOrElse(true))
                  )
                  StatusCodes.NoContent -> None
                }
            }
          }
      }
    }
}

case class ServiceRoute(resource: String, host: String, port: Int)

case class ServiceRoutes(services: List[ServiceRoute])

case class RegisterServiceRoute(name: String, host: String, resource: String, port: Option[Int] = None, secured: Option[Boolean] = None)
