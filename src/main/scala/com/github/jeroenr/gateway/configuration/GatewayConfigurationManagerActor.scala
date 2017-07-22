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
        gatewayConfiguration.targets.updated(target.resource, new GatewayTargetClient(target.address, target.port, target.secured))
      )
    case DeleteGatewayTarget(resource: String) =>
      gatewayConfiguration = gatewayConfiguration.copy(gatewayConfiguration.targets - resource)

    case GetGatewayConfig =>
      sender() ! gatewayConfiguration
  }
}

object GatewayConfigurationManagerActor {
  case class UpsertGatewayTarget(target: GatewayTarget)
  case class DeleteGatewayTarget(resource: String)
  case object GetGatewayConfig
}