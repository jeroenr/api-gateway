package com.github.cupenya.gateway.integration

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.model.{HttpRequest, _}
import akka.http.scaladsl.settings.ClientConnectionSettings
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import com.github.cupenya.gateway.{Config, Logging}
import spray.json._

import scala.concurrent.ExecutionContext

trait KubernetesClient extends Logging {
  implicit val system: ActorSystem
  implicit val ec: ExecutionContext
  implicit val materializer: Materializer

  lazy val client = Http(system).outgoingConnection(
    Config.integration.kubernetes.host,
    Config.integration.kubernetes.port,
    settings = ClientConnectionSettings(system)
  )

  val req = HttpRequest(GET, Uri(s"/api/v1/watch/services").withQuery(Query(Map("watch" -> "true")))).withHeaders(Connection("Keep-Alive"))

  def watchServices(): Unit = {
    Source
      .single(req)
      .via(client)
      .runWith(Sink.head).map { res =>
        res.entity.dataBytes
          .map(_.utf8String)
          .mapConcat(_.split('\n').toList)
          .map(_.parseJson)
          .map(serviceUpdateJson => {
            log.info(s"Service update: $serviceUpdateJson")
            serviceUpdateJson
          })
          .collect {
            case obj: JsObject =>
              obj.fields("object")
                .asJsObject.fields("metadata")
                .asJsObject.fields.get("labels")
                .flatMap(_.asJsObject.fields.get("resource"))
          }.collect {
            case Some(resourceName: JsString) => resourceName
          }.map(resource => log.info(s"Resource modified $resource"))
          .runWith(Sink.ignore)
      }
  }

}
