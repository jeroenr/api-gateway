package com.github.jeroenr

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}

import scala.concurrent.ExecutionContextExecutor

trait ApiGatewayHttpService extends Config {
  implicit val system: ActorSystem

  implicit def ec: ExecutionContextExecutor

  implicit val materializer: Materializer

  val gatewayRoute = Route { context =>
    val request = context.request
    val flow = Http(system).outgoingConnection("localhost", 80)
    val handler = Source.single(context.request)
      .via(flow)
      .runWith(Sink.head)
      .flatMap(context.complete(_))
    handler
  }
}
