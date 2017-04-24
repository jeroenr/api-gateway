package com.github.cupenya.gateway.server

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server.Directives

import scala.concurrent.duration._

trait CorsRoute extends Directives with CorsDirectives with SprayJsonSupport {
  val corsRoute =
    defaultCORSHeaders {
      options {
        complete(StatusCodes.OK -> None)
      }
    }
}

trait CorsDirectives { this: Directives =>
  private def ALLOWED_HEADERS = Seq(
    "Origin",
    "X-Requested-With",
    "Content-Type",
    "Accept",
    "Accept-Encoding",
    "Accept-Language",
    "Host",
    "Referer",
    "User-Agent",
    "Overwrite",
    "Destination",
    "Depth",
    "X-Token",
    "X-File-Size",
    "If-Modified-Since",
    "X-File-Name",
    "Cache-Control",
    "x-api-key",
    "x-auth-cupenya",
    "x-api-version",
    "x-cpy-trace-token"
  )

  def defaultCORSHeaders = respondWithHeaders(
    `Access-Control-Allow-Origin`.*,
    `Access-Control-Allow-Methods`(GET, POST, OPTIONS, DELETE,
      CONNECT, DELETE, HEAD, PATCH, PUT, TRACE),
    `Access-Control-Allow-Headers`(ALLOWED_HEADERS.mkString(", ")),
    `Access-Control-Allow-Credentials`(allow = true),
    `Access-Control-Max-Age`(1.hour.toSeconds)
  )
}