package com.github.cupenya.gateway

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.stream.ActorMaterializer
import com.github.cupenya.gateway.client.AuthServiceClient
import com.github.cupenya.gateway.health._
import com.github.cupenya.gateway.integration._
import com.github.cupenya.gateway.server.{ApiDashboardService, CorsRoute, GatewayHttpService}

object Boot extends App
    with Logging
    with GatewayHttpService
    with ApiDashboardService
    with HealthCheckRoute
    with HealthCheckService
    with CorsRoute {

  implicit val system = ActorSystem()
  implicit val ec = system.dispatcher
  implicit val materializer = ActorMaterializer()

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

  val serviceDiscoveryAgent =
    //        system.actorOf(Props(new ServiceDiscoveryAgent[StaticServiceUpdate](new StaticServiceListSource)))
    system.actorOf(Props(new ServiceDiscoveryAgent[KubernetesServiceUpdate](new KubernetesServiceDiscoveryClient)))

  serviceDiscoveryAgent ! ServiceDiscoveryAgent.WatchServices

  override def checks: List[HealthCheck] = List(new ServiceDiscoveryHealthCheck(serviceDiscoveryAgent), new AuthServiceHealthCheck(authClient))
}
