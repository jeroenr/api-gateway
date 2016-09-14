package com.github.cupenya.gateway.integration

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.model.{ HttpRequest, _ }
import akka.http.scaladsl.settings.ClientConnectionSettings
import akka.stream.Materializer
import akka.stream.scaladsl.{ Sink, Source }
import com.github.cupenya.gateway.{ Config, Logging }
import spray.json._

import scala.language.postfixOps
import scala.concurrent.{ ExecutionContext, Future }

class KubernetesServiceDiscoveryClient()(implicit system: ActorSystem, ec: ExecutionContext, materializer: Materializer)
    extends ServiceDiscoverySource[KubernetesServiceUpdate] with KubernetesServiceUpdateParser with Logging {

  lazy val client = Http(system).outgoingConnection(
    Config.integration.kubernetes.host,
    Config.integration.kubernetes.port,
    settings = ClientConnectionSettings(system)
  )

  private val req = HttpRequest(GET, Uri(s"/api/v1/watch/services").withQuery(Query(Map("watch" -> "true"))))
    .withHeaders(Connection("Keep-Alive"))

  def source: Future[Source[KubernetesServiceUpdate, _]] = Source
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
          case obj: JsObject => toKubernetesServiceUpdate(obj)
        }
        .collect {
          case Some(kubernetesServiceUpdate) =>
            log.info(s"Got Kubernetes service update $kubernetesServiceUpdate")
            kubernetesServiceUpdate
        }
    }

  override def name: String = "Kubernetes API"
}

trait KubernetesServiceUpdateParser extends DefaultJsonProtocol with Logging {

  case class PortMapping(protocol: String, port: Int, targetPort: Int, nodePort: Option[Int])

  case class Spec(ports: List[PortMapping])

  case class Metadata(uid: String, name: String, namespace: String, labels: Option[Map[String, String]])

  case class ServiceObject(spec: Spec, metadata: Metadata)

  case class ServiceMutation(`type`: UpdateType, `object`: ServiceObject)

  implicit val portMappingFormat = jsonFormat4(PortMapping)
  implicit val specFormat = jsonFormat1(Spec)
  implicit val metadataFormat = jsonFormat4(Metadata)
  implicit val serviceObjectFormat = jsonFormat2(ServiceObject)

  implicit object UpdateTypeFormat extends RootJsonFormat[UpdateType] {
    override def read(json: JsValue): UpdateType = json match {
      case JsString("ADDED") => UpdateType.Addition
      case JsString("DELETED") => UpdateType.Deletion
      case JsString("MODIFIED") => UpdateType.Mutation
      case _ =>
        throw DeserializationException(s"Couldn't deserialize $json. Was expecting one of [ADDED, DELETED, MODIFIED]")
    }

    override def write(updateType: UpdateType): JsValue = updateType match {
      case UpdateType.Addition => JsString("ADDED")
      case UpdateType.Deletion => JsString("DELETED")
      case UpdateType.Mutation => JsString("MODIFIED")
    }
  }

  implicit val serviceMutationFormat = jsonFormat2(ServiceMutation)

  val DEFAULT_PORT = 8080

  def toKubernetesServiceUpdate(jsObject: JsObject): Option[KubernetesServiceUpdate] = {
    val serviceMutation = jsObject.convertTo[ServiceMutation]
    val serviceObject = serviceMutation.`object`
    val metadata: Metadata = serviceObject.metadata
    metadata.labels.flatMap(_.get("resource")).map { resource =>
      KubernetesServiceUpdate(
        serviceMutation.`type`,
        cleanMetadataString(metadata.name),
        cleanMetadataString(resource),
        serviceObject.spec.ports.headOption.map(_.port).getOrElse(DEFAULT_PORT)
      )
    }
  }

  private def cleanMetadataString(value: String) =
    value.filterNot('"' ==)
}

sealed trait KubernetesNamespace {
  val ns: String
}

trait DefaultKubernetesNamespace extends KubernetesNamespace {
  override val ns: String = "default"
}

trait DiscoverableThroughDns extends DiscoverableAddress {
  self: KubernetesNamespace =>
  val name: String
  def address: String = s"$name.$ns"
}

case class KubernetesServiceUpdate(updateType: UpdateType, name: String, resource: String, port: Int = 8080)
  extends ServiceUpdate
  with DiscoverableThroughDns
  with DefaultKubernetesNamespace
