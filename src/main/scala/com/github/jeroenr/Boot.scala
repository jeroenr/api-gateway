package com.github.jeroenr

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer

object Boot extends App with Config with ApiGatewayHttpService with Logging {
  implicit val system = ActorSystem()
  implicit val ec = system.dispatcher
  implicit val materializer = ActorMaterializer()

  log.info(s"Starting API gateway using config $httpConfig")
  Http().bindAndHandle(gatewayRoute, httpConfig.interface, httpConfig.port).transform(
    binding => log.info(s"REST interface bound to ${binding.localAddress} "), { t => log.error(s"Couldn't start API gateway", t); sys.exit(1) }
  )
}
