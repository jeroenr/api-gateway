package com.github.cupenya.gateway.integration

import akka.actor.{ Actor, ActorRef, ActorSystem, Cancellable }
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

  val SERVICE_POLLING_INTERVAL = Config.integration.polling.interval

  private val watchingServices: Receive = {
    case HealthCheck => handleHealthCheck(sender())
  }

  private val bootstrapping: Receive = {
    case WatchServices =>
      log.info(s"Starting watching services")
      context.become(watchingServices)
      watchServices()
    case HealthCheck => handleHealthCheck(sender())
  }

  private def handleHealthCheck(sender: ActorRef): Unit = {
    serviceDiscoverySource.healthCheck.onComplete {
      case Success(_) =>
        log.info("Service discovery is healthy")
        sender ! ServiceDiscoveryHealthCheck.OK
      case Failure(t) =>
        log.error(s"Service discovery is unhealthy: ${t.getMessage}", t)
        sender ! ServiceDiscoveryHealthCheck.NOK
    }
  }

  def watchServices(): Unit = {
    system.scheduler.schedule(SERVICE_POLLING_INTERVAL seconds, 5 seconds) {
      serviceDiscoverySource.listServices.map(handleServiceUpdates)
    }
  }

  override def receive: Receive = bootstrapping

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
