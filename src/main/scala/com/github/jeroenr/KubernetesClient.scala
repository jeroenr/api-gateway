package com.github.jeroenr

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.settings.ClientConnectionSettings
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._

import scala.concurrent.ExecutionContext

trait KubernetesClient extends Logging {
  implicit val system: ActorSystem
  implicit val ec: ExecutionContext
  implicit val materializer: Materializer

  lazy val client = Http(system).outgoingConnection("localhost", 8080, settings = ClientConnectionSettings(system))

  val req = HttpRequest(GET, Uri(s"/api/v1/watch/services").withQuery(Query(Map("watch" -> "true")))).withHeaders(Connection("Keep-Alive"))

  def watchServices(): Unit = {
    Source
      .single(req)
      .via(client)
      .runWith(Sink.head).map { res =>
      res.entity.dataBytes.map(x => log.info(s"Service update: ${x.utf8String}")).runWith(Sink.ignore)
    }
  }

}
