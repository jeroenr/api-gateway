package com.github.jeroenr.gateway.configuration

import akka.actor.Actor
import akka.stream.Materializer
import com.github.jeroenr.gateway.Logging
import com.github.jeroenr.gateway.client.GatewayTargetClient
import com.github.jeroenr.gateway.model.GatewayTarget

class GatewayConfigurationManagerActor()(implicit mat: Materializer) extends Actor with Logging {

  implicit val system = context.system
  implicit val ec = context.dispatcher

  var gatewayConfiguration: GatewayConfiguration = _

  import com.github.jeroenr.gateway.configuration.GatewayConfigurationManagerActor._

  override def preStart(): Unit = {
    gatewayConfiguration = GatewayConfiguration(Map.empty[String, GatewayTargetClient])
  }

  override def receive: Receive = {
    case UpsertGatewayTarget(target: GatewayTarget) =>
      gatewayConfiguration = gatewayConfiguration.copy(
        gatewayConfiguration.targets.updated(target.resource, gatewayTargetClient(target))
      )
    case DeleteGatewayTarget(resource: String) =>
      gatewayConfiguration = gatewayConfiguration.copy(gatewayConfiguration.targets - resource)

    case SetGatewayTargets(targets) =>
      gatewayConfiguration = GatewayConfiguration(targets.map(target =>
        target.resource -> gatewayTargetClient(target)).toMap)
    case GetGatewayConfig =>
      sender() ! gatewayConfiguration
  }

  private def gatewayTargetClient(target: GatewayTarget) =
    GatewayTargetClient(target.address, target.port, target.secured)
}

object GatewayConfigurationManagerActor {
  case class SetGatewayTargets(targets: List[GatewayTarget])
  case class UpsertGatewayTarget(target: GatewayTarget)
  case class DeleteGatewayTarget(resource: String)
  case object GetGatewayConfig
}