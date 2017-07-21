package com.github.jeroenr.gateway.client

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model._
import akka.http.scaladsl.settings.ClientConnectionSettings
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import akka.stream.scaladsl.{ Sink, Source }
import com.github.jeroenr.gateway.Logging
import spray.json._

import scala.concurrent.{ ExecutionContext, Future }

class AuthServiceClient(host: String, port: Int)(
    implicit
    val system: ActorSystem, ec: ExecutionContext, materializer: Materializer
) extends DefaultJsonProtocol with SprayJsonSupport with Logging {

  implicit val jwtFormat = jsonFormat1(JwtTokenResponse)
  implicit val loginDataFormat = jsonFormat2(LoginData)

  private lazy val client = Http(system).outgoingConnection(host, port, settings = ClientConnectionSettings(system))

  def getToken(headers: Seq[HttpHeader]): Future[Either[HttpResponse, JwtTokenResponse]] = {
    log.debug(s"Getting token with headers $headers")
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

  def login(loginData: LoginData): Future[HttpResponse] = {
    Source
      .single(Post("/auth/login")
        .withEntity(`application/json`, loginData.toJson.compactPrint))
      .via(client)
      .runWith(Sink.head)
  }

  def logout: Future[HttpResponse] = {
    Source
      .single(Post("/auth/logout"))
      .via(client)
      .runWith(Sink.head)
  }

  def currentUser(headers: Seq[HttpHeader]): Future[HttpResponse] = {
    Source
      .single(Get("/auth/currentUser").withHeaders(headers: _*))
      .via(client)
      .runWith(Sink.head)
  }

  def health: Future[HttpResponse] =
    Source.single(Get("/health")).via(client).runWith(Sink.head)
}

case class JwtTokenResponse(jwt: String)

case class LoginData(username: String, password: String)
