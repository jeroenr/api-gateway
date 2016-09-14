package com.github.cupenya.gateway.integration

import akka.actor.{Actor, ActorSystem, Cancellable}
import akka.stream.Materializer
import com.github.cupenya.gateway.configuration.GatewayConfigurationManager
import com.github.cupenya.gateway.health.ServiceDiscoveryHealthCheck
import com.github.cupenya.gateway.model.GatewayTarget
import com.github.cupenya.gateway.{Config, Logging}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success}

class ServiceDiscoveryAgent[T <: ServiceUpdate](serviceDiscoverySource: ServiceDiscoverySource[T])(
  implicit materializer: Materializer
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
      watchServices()
    case HealthCheck => sender() ! ServiceDiscoveryHealthCheck.NOK
  }

  def watchServices(): Unit = serviceDiscoverySource.source.map(_.runForeach(serviceUpdate => {
    log.info(s"Service modified $serviceUpdate")
    registerService(serviceUpdate)
  }).onComplete {
    case Success(done) =>
      log.warn(s"Service discovery stream ended unexpectedly with '$done'. " +
        s"Reconnecting in $RECONNECT_DELAY_IN_SECONDS seconds")
      tryReconnect
    case Failure(t) =>
      log.error(s"Service discovery stream failed. Reconnecting in $RECONNECT_DELAY_IN_SECONDS seconds.", t)
      tryReconnect
  }).onComplete {
    case Success(_) =>
      log.info(s"Successfully connected to service discovery source '${serviceDiscoverySource.name}'.")
      context.become(connected)
    case Failure(t) =>
      log.error(s"Failed to connect to service discovery source '${serviceDiscoverySource.name}'. " +
        s"Retrying in $RECONNECT_DELAY_IN_SECONDS seconds.", t)
      tryReconnect
  }

  private def tryReconnect: Cancellable = {
    context.become(disconnected)
    context.system.scheduler.scheduleOnce(RECONNECT_DELAY_IN_SECONDS seconds, self, WatchServices)
  }

  override def receive: Receive = disconnected

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

  case object HealthCheck

}
