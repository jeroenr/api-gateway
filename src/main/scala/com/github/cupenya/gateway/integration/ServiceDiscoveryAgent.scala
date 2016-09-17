package com.github.cupenya.gateway.integration

import akka.actor.{ Actor, ActorSystem, Cancellable }
import akka.stream.Materializer
import com.github.cupenya.gateway.configuration.GatewayConfigurationManager
import com.github.cupenya.gateway.health.ServiceDiscoveryHealthCheck
import com.github.cupenya.gateway.model.GatewayTarget
import com.github.cupenya.gateway.{ Config, Logging }

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{ Failure, Success }

class ServiceDiscoveryAgent[T <: ServiceUpdate](serviceDiscoverySource: ServiceDiscoverySource[T])(
    implicit
    materializer: Materializer
) extends Actor with Logging {

  import ServiceDiscoveryAgent._

  implicit val system: ActorSystem = context.system
  implicit val ec: ExecutionContext = context.dispatcher

  val RECONNECT_DELAY_IN_SECONDS = Config.integration.reconnect.delay

  private val connected: Receive = {
    case HealthCheck => sender() ! ServiceDiscoveryHealthCheck.OK
  }

  private val disconnected: Receive = {
    case WatchServices =>
      log.info(s"Starting watching services")
      // TODO: fix health check
      watchServices()
    case HealthCheck => sender() ! ServiceDiscoveryHealthCheck.NOK
  }

  def watchServices(): Unit = {
    system.scheduler.schedule(RECONNECT_DELAY_IN_SECONDS seconds, 5 seconds) {
      serviceDiscoverySource.source.map(handleServiceUpdates)
    }
  }

  private def tryReconnect: Cancellable = {
    context.become(disconnected)
    context.system.scheduler.scheduleOnce(RECONNECT_DELAY_IN_SECONDS seconds, self, WatchServices)
  }

  override def receive: Receive = disconnected

  private def handleServiceUpdates(serviceUpdates: List[T]) = {
    val currentResources = GatewayConfigurationManager.currentConfig().targets.keys.toList
    val toDelete = currentResources.filterNot(serviceUpdates.map(_.resource).contains)
    log.info(s"Deleting $toDelete")
    toDelete.foreach(GatewayConfigurationManager.deleteGatewayTarget)

    val newResources = serviceUpdates.filterNot(su => currentResources.contains(su.resource))
    log.info(s"New services $newResources")
    newResources.foreach(serviceUpdate => {
      val gatewayTarget = GatewayTarget(serviceUpdate.resource, serviceUpdate.address, serviceUpdate.port)
      log.info(s"Registering new gateway target $gatewayTarget")
      GatewayConfigurationManager.upsertGatewayTarget(gatewayTarget)
    })
  }
}

object ServiceDiscoveryAgent {

  case object WatchServices

  case object HealthCheck

}
