package com.github.cupenya.gateway.integration

import akka.actor.{ActorSystem, Actor}
import akka.stream.Materializer
import com.github.cupenya.gateway.Logging
import com.github.cupenya.gateway.configuration.GatewayConfigurationManager
import com.github.cupenya.gateway.model.GatewayTarget

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

class ServiceDiscoveryAgent[T <: ServiceUpdate](serviceDiscoverySource: ServiceDiscoverySource[T])(
  implicit materializer: Materializer) extends Actor with Logging {

  import ServiceDiscoveryAgent._

  implicit val system: ActorSystem = context.system
  implicit val ec: ExecutionContext = context.dispatcher

  def watchServices(): Unit = serviceDiscoverySource.source.map(_.runForeach(serviceUpdate => {
    log.info(s"Service modified $serviceUpdate")
    registerService(serviceUpdate)
  }).onComplete {
    case Success(done) =>
      log.warn(s"Service discovery stream ended $done")
      self ! WatchServices
    case Failure(t) =>
      log.error(s"Service discovery stream failed", t)
      self ! WatchServices
  })

  override def receive: Receive = {
    case WatchServices =>
      log.info(s"Starting watching services")
      watchServices()
  }

  private def registerService(serviceUpdate: T): Unit = {
    serviceUpdate.updateType match {
      case UpdateType.Addition | UpdateType.Mutation =>
        val gatewayTarget = GatewayTarget(serviceUpdate.resource, serviceUpdate.address, serviceUpdate.port)
        log.info(s"Registering new gateway target $gatewayTarget")
        GatewayConfigurationManager.upsertGatewayTarget(gatewayTarget)
      case UpdateType.Deletion =>
        log.info(s"Deleting gateway target ${serviceUpdate.address}")
        GatewayConfigurationManager.deleteGatewayTarget(serviceUpdate.resource)
    }
  }
}

object ServiceDiscoveryAgent {
  case object WatchServices
}
