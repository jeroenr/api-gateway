package com.github.cupenya.gateway.integration

import akka.stream.scaladsl.Source
import com.github.cupenya.gateway.Logging

import scala.concurrent.Future

sealed trait UpdateType

object UpdateType {

  case object Addition extends UpdateType

  case object Mutation extends UpdateType

  case object Deletion extends UpdateType

}

trait DiscoverableAddress {
  def address: String
}

trait ServiceUpdate extends DiscoverableAddress {
  def updateType: UpdateType
  def resource: String
  def port: Int
}

trait ServiceDiscoverySource[T <: ServiceUpdate] extends Logging {
  def name: String
  def source: Future[Source[T, _]]
}
