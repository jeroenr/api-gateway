package com.github.jeroenr.gateway

import akka.actor.{ ActorSystem, Props }
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.stream.ActorMaterializer
import com.github.jeroenr.gateway.client.AuthServiceClient
import com.github.jeroenr.gateway.health._
import com.github.jeroenr.service.discovery._
import com.github.jeroenr.service.discovery.health._
import com.github.jeroenr.gateway.server.{ ApiDashboardService, CorsRoute, GatewayHttpService }
import com.github.jeroenr.gateway.configuration._
import com.github.jeroenr.gateway.model._
import akka.pattern._
import akka.util.Timeout
import scala.language.postfixOps

object Boot extends App
    with GatewayHttpService
    with ApiDashboardService
    with HealthCheckRoute
    with HealthCheckService
    with CorsRoute {

  implicit val system = ActorSystem()
  implicit val ec = system.dispatcher
  implicit val materializer = ActorMaterializer()
  implicit val timeout = Timeout(Config.DEFAULT_TIMEOUT)

  private val gatewayInterface = Config.gateway.interface
  private val gatewayPort = Config.gateway.port

  private val dashboardInterface = Config.dashboard.interface
  private val dashboardPort = Config.dashboard.port

  val authClient = new AuthServiceClient(
    Config.integration.authentication.host,
    Config.integration.authentication.port
  )

  log.info(s"Starting API gateway using gateway interface $gatewayInterface and port $gatewayPort")

  val rootRoute =
    defaultCORSHeaders {
      options {
        complete(StatusCodes.OK -> None)
      } ~ authRoute ~ healthRoute ~ gatewayRoute
    }

  val mainDashboardRoute =
    defaultCORSHeaders {
      options {
        complete(StatusCodes.OK -> None)
      } ~ dashboardRoute
    }
  Http().bindAndHandle(rootRoute, gatewayInterface, gatewayPort).transform(
    binding => log.info(s"REST gateway interface bound to ${binding.localAddress} "), { t => log.error(s"Couldn't start API gateway", t); sys.exit(1) }
  )

  log.info(s"Starting API gateway dashboard using interface $dashboardInterface and port $dashboardPort")

  Http().bindAndHandle(mainDashboardRoute, dashboardInterface, dashboardPort).transform(
    binding => log.info(s"REST gateway dashboard interface bound to ${binding.localAddress} "), { t => log.error(s"Couldn't start API gateway dashboard", t); sys.exit(1) }
  )

  val gatewayConfigurationManager = system.actorOf(Props(new GatewayConfigurationManagerActor))

  private def handleServiceUpdates[T <: ServiceUpdate](allServiceUpdates: List[T]) = {
    val serviceUpdates = allServiceUpdates.filter { upd =>
      Config.integration.kubernetes.namespaces.isEmpty || Config.integration.kubernetes.namespaces.contains(upd.namespace)
    }
    (gatewayConfigurationManager ? GatewayConfigurationManagerActor.GetGatewayConfig).mapTo[GatewayConfiguration].map { currentConfig =>
      val currentResources = currentConfig.targets.keys.toList
      val toDelete = currentResources.filterNot(serviceUpdates.map(_.resource).contains)
      log.debug(s"Deleting $toDelete")
      toDelete.foreach(gatewayConfigurationManager ! GatewayConfigurationManagerActor.DeleteGatewayTarget(_))

      // TODO: handle config updates
      val newResources = serviceUpdates.filterNot(su => currentResources.contains(su.resource))
      log.debug(s"New services $newResources")
      newResources.foreach(serviceUpdate => {
        val gatewayTarget =
          GatewayTarget(serviceUpdate.resource, serviceUpdate.address, serviceUpdate.port, serviceUpdate.secured)
        log.info(s"Registering new gateway target $gatewayTarget")
        gatewayConfigurationManager ! GatewayConfigurationManagerActor.UpsertGatewayTarget(gatewayTarget)
      })
    }

  }

  val serviceDiscoveryAgent =
    //        system.actorOf(Props(new ServiceDiscoveryAgent[StaticServiceUpdate](new StaticServiceListSource)))
    system.actorOf(Props(new ServiceDiscoveryAgent[KubernetesServiceUpdate](new KubernetesServiceDiscoveryClient, handleServiceUpdates)))

  serviceDiscoveryAgent ! ServiceDiscoveryAgent.WatchServices

  override def checks: List[HealthCheck] = List(new ServiceDiscoveryHealthCheck(serviceDiscoveryAgent), new AuthServiceHealthCheck(authClient))
}
