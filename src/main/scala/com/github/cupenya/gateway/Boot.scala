package com.github.cupenya.gateway

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.github.cupenya.gateway.configuration.GatewayConfigurationManager
import com.github.cupenya.gateway.integration.KubernetesClient
import com.github.cupenya.gateway.server.{ApiDashboardService, GatewayHttpService}

object Boot extends App with Config with Logging with GatewayHttpService with GatewayConfigurationManager with ApiDashboardService with KubernetesClient {
  implicit val system = ActorSystem()
  implicit val ec = system.dispatcher
  implicit val materializer = ActorMaterializer()

  log.info(s"Starting API gateway using interface $interface and port $httpPort")

  Http().bindAndHandle(gatewayRoute, interface, httpPort).transform(
    binding => log.info(s"REST interface bound to ${binding.localAddress} "), { t => log.error(s"Couldn't start API gateway", t); sys.exit(1) }
  )

  Http().bindAndHandle(dashboardRoute, interface, httpPort + 1).transform(
    binding => log.info(s"REST interface bound to ${binding.localAddress} "), { t => log.error(s"Couldn't start API gateway dashboard", t); sys.exit(1) }
  )

  log.info(s"Starting watching services")
  watchServices()

}
