package com.github.jeroenr.gateway

import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.headers.{ ModeledCompanion, `Remote-Address` }

package object client {
  implicit class RichHeaders(headers: List[HttpHeader]) {

    def noEmptyHeaders: List[HttpHeader] =
      headers.filterNot(_.value.isEmpty)

    def valueOf[T](header: ModeledCompanion[T]): Option[String] =
      headers.find(_.is(`Remote-Address`.lowercaseName)).map(_.value)

    def -[T](header: ModeledCompanion[T]): List[HttpHeader] =
      headers.filterNot(_.is(header.lowercaseName))
  }
}
