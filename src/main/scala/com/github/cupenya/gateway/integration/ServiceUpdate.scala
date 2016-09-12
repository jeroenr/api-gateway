package com.github.cupenya.gateway.integration

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.github.cupenya.gateway.Logging
import com.github.cupenya.gateway.configuration.GatewayConfigurationManager
import com.github.cupenya.gateway.model.GatewayTarget

import scala.concurrent.ExecutionContext

trait DiscoverableAddress {
  def address: String
}

trait ServiceUpdate extends DiscoverableAddress {
  def resource: String
  def port: Int
}

trait ServiceDiscoverySource extends Logging {
  implicit val system: ActorSystem
  implicit val ec: ExecutionContext
  implicit val materializer: Materializer

  // TODO: handle different types of update
  def registerService(serviceUpdate: ServiceUpdate): Unit = {
    val gatewayTarget = GatewayTarget(serviceUpdate.resource, serviceUpdate.address, serviceUpdate.port)
    log.info(s"Registering new gateway target $gatewayTarget")
    GatewayConfigurationManager.upsertGatewayTarget(gatewayTarget)
  }
}
