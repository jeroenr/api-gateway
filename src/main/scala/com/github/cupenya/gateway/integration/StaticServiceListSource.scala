package com.github.cupenya.gateway.integration

import scala.concurrent.Future

class StaticServiceListSource extends ServiceDiscoverySource[StaticServiceUpdate] {
  val DEFAULT_PORT = 80

  override def listServices: Future[List[StaticServiceUpdate]] =
    Future.successful(
      List(StaticServiceUpdate(UpdateType.Addition, "hello", "micro.cupenya.com", DEFAULT_PORT, secured = false))
    )

  override def name: String = "static service list"

  override def healthCheck: Future[_] = Future.successful()
}

case class StaticServiceUpdate(updateType: UpdateType, resource: String, address: String, port: Int, secured: Boolean) extends ServiceUpdate