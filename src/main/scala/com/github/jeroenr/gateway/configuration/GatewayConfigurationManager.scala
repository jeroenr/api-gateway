package com.github.jeroenr.gateway.configuration

import java.util.concurrent.atomic.AtomicReference

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.github.jeroenr.gateway.client.GatewayTargetClient
import com.github.jeroenr.gateway.model.GatewayTarget

import scala.concurrent.ExecutionContext

object GatewayConfigurationManager {
  private val configHolder = new AtomicReference[GatewayConfiguration](GatewayConfiguration(Map.empty[String, GatewayTargetClient]))

  def currentConfig(): GatewayConfiguration =
    configHolder.get()

  def upsertGatewayTarget(target: GatewayTarget)(implicit system: ActorSystem, ec: ExecutionContext, materializer: Materializer): Unit = {
    val current = configHolder.get()
    configHolder.lazySet(current.copy(
      current.targets.updated(target.resource, new GatewayTargetClient(target.address, target.port, target.secured))
    ))
  }

  def deleteGatewayTarget(resource: String): Unit = {
    val current = configHolder.get()
    configHolder.lazySet(current.copy(current.targets - resource))
  }
}
