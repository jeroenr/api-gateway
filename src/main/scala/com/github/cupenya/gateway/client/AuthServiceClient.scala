package com.github.cupenya.gateway.client

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model._
import akka.http.scaladsl.settings.ClientConnectionSettings
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import spray.json._

import scala.concurrent.{ExecutionContext, Future}

class AuthServiceClient(host: String, port: Int)(implicit
                                                 val system: ActorSystem, ec: ExecutionContext, materializer: Materializer) extends DefaultJsonProtocol with SprayJsonSupport {
  implicit val jwtFormat = jsonFormat1(JwtTokenResponse)
  implicit val errorFormat = jsonFormat1(ErrorResponse)

  val client = Http(system).outgoingConnection(host, port, settings = ClientConnectionSettings(system))

  def getToken(headers: Seq[HttpHeader]): Future[Either[ErrorResponse, JwtTokenResponse]] = {
    Source
      .single(Get("auth/token").withHeaders(headers: _*))
      .via(client)
      .runWith(Sink.head)
      .flatMap { res =>
        res.status match {
          case StatusCodes.OK => Unmarshal(res.entity).to[JwtTokenResponse].map(Right.apply)
          case _ => Unmarshal(res.entity).to[ErrorResponse].map(Left.apply)
        }
      }
  }
}

case class JwtTokenResponse(jwt: String)

case class ErrorResponse(error: String)
