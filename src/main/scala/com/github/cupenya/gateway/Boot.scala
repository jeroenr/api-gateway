package com.github.cupenya.gateway

import akka.actor.{ ActorSystem, Props }
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.github.cupenya.gateway.health.{ HealthCheck, HealthCheckRoute, HealthCheckService, ServiceDiscoveryHealthCheck }
import com.github.cupenya.gateway.integration._
import com.github.cupenya.gateway.server.{ ApiDashboardService, GatewayHttpService }

object Boot extends App
    with Logging
    with GatewayHttpService
    with ApiDashboardService
    with HealthCheckRoute
    with HealthCheckService {

  implicit val system = ActorSystem()
  implicit val ec = system.dispatcher
  implicit val materializer = ActorMaterializer()

  private val gatewayInterface = Config.gateway.interface
  private val gatewayPort = Config.gateway.port

  private val dashboardInterface = Config.dashboard.interface
  private val dashboardPort = Config.dashboard.port

  log.info(s"Starting API gateway using gatewayInterface $gatewayInterface and port $gatewayPort")

  Http().bindAndHandle(gatewayRoute, gatewayInterface, gatewayPort).transform(
    binding => log.info(s"REST gatewayInterface bound to ${binding.localAddress} "), { t => log.error(s"Couldn't start API gateway", t); sys.exit(1) }
  )

  log.info(s"Starting API gateway dashboard using gatewayInterface $dashboardInterface and port $dashboardPort")

  Http().bindAndHandle(dashboardRoute ~ healthRoute, dashboardInterface, dashboardPort).transform(
    binding => log.info(s"REST gatewayInterface bound to ${binding.localAddress} "), { t => log.error(s"Couldn't start API gateway dashboard", t); sys.exit(1) }
  )

  val serviceDiscoveryAgent =
    //    system.actorOf(Props(new ServiceDiscoveryAgent[StaticServiceUpdate](new StaticServiceListSource)))
    system.actorOf(Props(new ServiceDiscoveryAgent[KubernetesServiceUpdate](new KubernetesServiceDiscoveryClient)))

  serviceDiscoveryAgent ! ServiceDiscoveryAgent.WatchServices

  override def checks: List[HealthCheck] = List(new ServiceDiscoveryHealthCheck(serviceDiscoveryAgent))
}
