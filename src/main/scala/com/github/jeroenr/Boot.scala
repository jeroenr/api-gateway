package com.github.jeroenr

import akka.actor.{ ActorSystem, Props }
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer

object Boot extends App with Config with Logging with ApiGatewayRoute with RouteRepository with ApiDashboardService with KubernetesClient {
  implicit val system = ActorSystem()
  implicit val ec = system.dispatcher
  implicit val materializer = ActorMaterializer()

  log.info(s"Starting API gateway using config $httpConfig")

  Http().bindAndHandle(gatewayRoute, httpConfig.interface, httpConfig.port).transform(
    binding => log.info(s"REST interface bound to ${binding.localAddress} "), { t => log.error(s"Couldn't start API gateway", t); sys.exit(1) }
  )

  Http().bindAndHandle(dashboardRoute, httpConfig.interface, httpConfig.port + 1).transform(
    binding => log.info(s"REST interface bound to ${binding.localAddress} "), { t => log.error(s"Couldn't start API gateway", t); sys.exit(1) }
  )

  log.info(s"Starting watching services")
  watchServices()

}
