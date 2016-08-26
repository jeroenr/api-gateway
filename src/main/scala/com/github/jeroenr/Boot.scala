package com.github.jeroenr

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.github.jeroenr.RouteManager.AddServiceRoute

object Boot extends App with Config with Logging {
  implicit val system = ActorSystem()
  implicit val ec = system.dispatcher
  implicit val materializer = ActorMaterializer()

  log.info(s"Starting API gateway using config $httpConfig")

  val gatewayService = new ApiGatewayService

  private val routeManager = system.actorOf(Props(new RouteManager(gatewayService)))

  Http().bindAndHandle(gatewayService.gatewayRoute, httpConfig.interface, httpConfig.port).transform(
    binding => log.info(s"REST interface bound to ${binding.localAddress} "), { t => log.error(s"Couldn't start API gateway", t); sys.exit(1) }
  ).onComplete { _ =>
    routeManager ! AddServiceRoute("some route", "localhost", "my-resource")
  }


}
