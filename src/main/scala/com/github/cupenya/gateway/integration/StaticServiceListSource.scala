package com.github.cupenya.gateway.integration

import akka.stream.scaladsl.Source

import scala.concurrent.Future

class StaticServiceListSource extends ServiceDiscoverySource[StaticServiceUpdate] {
  override def source: Future[Source[StaticServiceUpdate, _]] =
    Future.successful(Source.fromIterator(() =>
      List(new StaticServiceUpdate(UpdateType.Addition, "health", "localhost", 9091)).toIterator)
    )
}

case class StaticServiceUpdate(updateType: UpdateType, resource: String, address: String, port: Int) extends ServiceUpdate