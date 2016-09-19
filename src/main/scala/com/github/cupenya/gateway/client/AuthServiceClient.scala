package com.github.cupenya.gateway.client

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model._
import akka.http.scaladsl.settings.ClientConnectionSettings
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import akka.stream.scaladsl.{ Sink, Source }
import com.github.cupenya.gateway.Logging
import spray.json._

import scala.concurrent.{ ExecutionContext, Future }

class AuthServiceClient(host: String, port: Int)(
  implicit val system: ActorSystem, ec: ExecutionContext, materializer: Materializer
) extends DefaultJsonProtocol with SprayJsonSupport with Logging {

  implicit val jwtFormat = jsonFormat1(JwtTokenResponse)

  private val client = Http(system).outgoingConnection(host, port, settings = ClientConnectionSettings(system))

  def getToken(headers: Seq[HttpHeader]): Future[Either[HttpResponse, JwtTokenResponse]] = {
    log.info(s"Getting token with headers $headers")
    Source
      .single(Get("/auth/token").withHeaders(headers: _*))
      .via(client)
      .runWith(Sink.head)
      .flatMap { res =>
        res.status match {
          case StatusCodes.OK => Unmarshal(res.entity).to[JwtTokenResponse].map(Right.apply)
          case _ => Future.successful(Left(res))
        }
      }
  }
}

case class JwtTokenResponse(jwt: String)
