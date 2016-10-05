package com.github.cupenya.gateway.server

import akka.actor.ActorSystem
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.server._
import akka.stream.Materializer
import com.github.cupenya.gateway.client.{ AuthServiceClient, GatewayTargetClient, LoginData }
import com.github.cupenya.gateway.configuration.{ GatewayConfiguration, GatewayConfigurationManager }
import com.github.cupenya.gateway.{ Config, Logging }
import spray.json.DefaultJsonProtocol

import scala.concurrent.ExecutionContext

trait GatewayTargetDirectives extends Directives {
  def serviceRouteForResource(config: GatewayConfiguration): Directive[Tuple1[GatewayTargetClient]] =
    pathPrefix(GatewayTargetPathMatcher(config)).flatMap(provide)
}

case class GatewayTargetPathMatcher(config: GatewayConfiguration) extends PathMatcher1[GatewayTargetClient] {
  import Path._
  import PathMatcher._

  def apply(path: Path): Matching[Tuple1[GatewayTargetClient]] =
    matchPathToGatewayTarget(path)

  private def matchPathToGatewayTarget(path: Path) = {
    path match {
      case s @ Segment(head, _) =>
        config.targets.get(head)
          .map(gatewayTarget => Matched(s, Tuple1(gatewayTarget)))
          .getOrElse(Unmatched)
      case _ => Unmatched
    }
  }
}

trait GatewayHttpService extends GatewayTargetDirectives
    with Logging
    with SprayJsonSupport
    with DefaultJsonProtocol
    with Directives {

  implicit def system: ActorSystem

  implicit def ec: ExecutionContext

  implicit val materializer: Materializer

  implicit val loginDataFormat = jsonFormat2(LoginData)

  val gatewayRoute: Route = (ctx: RequestContext) =>
    serviceRouteForResource(GatewayConfigurationManager.currentConfig())(_.route)(ctx)

  private lazy val authClient = new AuthServiceClient(
    Config.integration.authentication.host,
    Config.integration.authentication.port
  )

  val authRoute =
    pathPrefix("auth") {
      path("currentUser") {
        get {
          extractRequest { req =>
            complete(authClient.currentUser(req.headers))
          }
        }
      } ~
      path("login") {
        post {
          entity(as[LoginData]) { loginData =>
            complete(authClient.login(loginData))
          }
        }
      }
    }
}

