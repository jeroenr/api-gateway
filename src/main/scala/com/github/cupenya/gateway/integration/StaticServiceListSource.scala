package com.github.cupenya.gateway.integration

import akka.stream.scaladsl.Source

import scala.concurrent.Future

class StaticServiceListSource extends ServiceDiscoverySource[StaticServiceUpdate] {
  val DEFAULT_PORT = 9091

  override def source: Future[Source[StaticServiceUpdate, _]] =
    Future.successful(Source.fromIterator(() => {
      List(StaticServiceUpdate(UpdateType.Addition, "health", "localhost", DEFAULT_PORT)).toIterator
    })
    )
}

case class StaticServiceUpdate(updateType: UpdateType, resource: String, address: String, port: Int) extends ServiceUpdate